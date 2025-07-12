package com.home.java_02.domain.category.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.java_02.common.exception.ServiceException;
import com.home.java_02.common.exception.ServiceExceptionCode;
import com.home.java_02.domain.category.dto.CategoryRequest;
import com.home.java_02.domain.category.dto.CategoryResponse;
import com.home.java_02.domain.category.entity.Category;
import com.home.java_02.domain.category.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

  private final Jedis jedis;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final CategoryRepository categoryRepository;

  private static final String CACHE_KEY_CATEGORY_STRUCT = "categoryStruct";
  private static final int CACHE_EXPIRE_SECONDS = 3600; // 1시간

  private List<CategoryResponse> findCategoryStruct() {
    List<Category> categories = categoryRepository.findAll();

    Map<Long, CategoryResponse> categoryResponseMap = new HashMap<>();

    for (Category category : categories) {
      CategoryResponse response = CategoryResponse.builder()
          .id(category.getId())
          .name(category.getName())
          .categories(new ArrayList<>())
          .build();
      categoryResponseMap.put(category.getId(), response);
    }

    List<CategoryResponse> rootCategories = new ArrayList<>();
    for (Category category : categories) {
      CategoryResponse categoryResponse = categoryResponseMap.get(category.getId());

      if (ObjectUtils.isEmpty(category.getParent())) {
        rootCategories.add(categoryResponse);
      } else {
        CategoryResponse parentCategoryResponse = categoryResponseMap.get(
            category.getParent().getId());

        if (!ObjectUtils.isEmpty(parentCategoryResponse)) {
          parentCategoryResponse.getCategories().add(categoryResponse);
        }
      }
    }

    return rootCategories;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> findCategoryStructCacheAside() throws JsonProcessingException {
    String cachedCategories = jedis.get(CACHE_KEY_CATEGORY_STRUCT);

    if (!ObjectUtils.isEmpty(cachedCategories)) {
      return objectMapper.readValue(cachedCategories, new TypeReference<>() {
      });
    }

    List<CategoryResponse> rootCategories = findCategoryStruct();

    if (!ObjectUtils.isEmpty(rootCategories)) {
      String jsonString = objectMapper.writeValueAsString(rootCategories);
      jedis.setex(CACHE_KEY_CATEGORY_STRUCT, CACHE_EXPIRE_SECONDS, jsonString);
    }

    return rootCategories;
  }

  @Transactional
  public void saveWriteThrough(CategoryRequest request) {
    Category parentCategory = null;

    if (ObjectUtils.isEmpty(request.getCategoryId())) {
      parentCategory = categoryRepository.findById(request.getCategoryId())
          .orElseThrow(() -> new ServiceException(ServiceExceptionCode.NOT_FOUND_DATA));
    }

    Category category = Category.builder()
        .name(request.getName())
        .parent(parentCategory)
        .build();

    categoryRepository.save(category);

    updateCategoryStructCache();
  }

  private void updateCategoryStructCache() {
    try {
      List<CategoryResponse> rootCategories = findCategoryStruct();

      if (!ObjectUtils.isEmpty(rootCategories)) {
        String jsonString = objectMapper.writeValueAsString(rootCategories);
        jedis.setex(CACHE_KEY_CATEGORY_STRUCT, CACHE_EXPIRE_SECONDS, jsonString);
      }
    } catch (Exception e) {
      log.error("Error updating cache key {}: {}", CACHE_KEY_CATEGORY_STRUCT, e.getMessage());
    }
  }

  @Transactional
  public void saveWriteBack(CategoryRequest request) {
    try {
      String cachedData = jedis.get(CACHE_KEY_CATEGORY_STRUCT);
      List<CategoryResponse> categories = new ArrayList<>();

      if (StringUtils.hasText(cachedData)) {
        categories = objectMapper.readValue(cachedData, new TypeReference<>() {
        });
      }

      CategoryResponse newCategory = CategoryResponse.builder()
          .name(request.getName())
          .categories(new ArrayList<>())
          .build();

      if (ObjectUtils.isEmpty(request.getCategoryId())) {
        Map<Long, CategoryResponse> categoryMap = buildCategoryMap(categories);
        CategoryResponse parentCategory = categoryMap.get(request.getCategoryId());

        if (parentCategory != null) {
          parentCategory.getCategories().add(newCategory);
        } else {
          categories.add(newCategory);
        }

      } else {
        categories.add(newCategory);
      }

      String jsonString = objectMapper.writeValueAsString(categories);
      jedis.setex(CACHE_KEY_CATEGORY_STRUCT, CACHE_EXPIRE_SECONDS, jsonString);

      saveToDatabaseAsync(request);

    } catch (Exception e) {
      log.error("Write-back 패턴 저장 실패: {}", e.getMessage(), e);
    }
  }

  private Map<Long, CategoryResponse> buildCategoryMap(List<CategoryResponse> categories) {
    return categories.stream()
        .collect(HashMap::new,
            (map, category) -> {
              map.put(category.getId(), category);
              if (category.getCategories() != null) {
                map.putAll(buildCategoryMap(category.getCategories()));
              }
            },
            HashMap::putAll);
  }

  @Async
  public void saveToDatabaseAsync(CategoryRequest request) {
    try {
      Category parentCategory = null;

      if (ObjectUtils.isEmpty(request.getCategoryId())) {
        parentCategory = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new ServiceException(ServiceExceptionCode.NOT_FOUND_DATA));
      }

      Category newCategory = Category.builder()
          .name(request.getName())
          .parent(parentCategory)
          .build();

      categoryRepository.save(newCategory);

    } catch (Exception e) {
      log.error("비동기 DB 저장 실패: {}", e.getMessage(), e);
    }
  }
}