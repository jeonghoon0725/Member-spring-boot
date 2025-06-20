package com.home.java_02;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
- **`CategoryFlatDto` 클래스**
    - DB에서 조회된 원본 데이터를 표현하는 클래스입니다.
    - **필드**: `id(Long)`, `name(String)`, `parentId(Long)`

- **`CategoryTreeDto` 클래스**
    - 최종 결과물인 계층 구조를 표현하는 클래스입니다.
    - **필드**: `id(Long)`, `name(String)`, `children(List<CategoryTreeDto>)`

 */

class CategoryFlatDto {

  Long id;
  String name;
  Long parentId;

  public CategoryFlatDto(Long id, String name, Long parentId) {
    this.id = id;
    this.name = name;
    this.parentId = parentId;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Long getParentId() {
    return parentId;
  }
}

class CategoryTreeDto {

  Long id;
  String name;
  List<CategoryTreeDto> children = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<CategoryTreeDto> getChildren() {
    return children;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }
}


public class JavaExample {


  static class CategoryConverter {

    public List<CategoryTreeDto> convertToTree(List<CategoryFlatDto> categoryFlatList) {
      Map<Long, CategoryTreeDto> idToNodeMap = new HashMap<>();
      List<CategoryTreeDto> rootList = new ArrayList<>();

      // 1. map 으로 노드 생성
      for (CategoryFlatDto flatDto : categoryFlatList) {
        CategoryTreeDto categoryTreeDto = new CategoryTreeDto();
        categoryTreeDto.setId(flatDto.getId());
        categoryTreeDto.setName(flatDto.getName());
        idToNodeMap.put(flatDto.getId(), categoryTreeDto);
      }

      // 2. 부모-자식 연결
      for (CategoryFlatDto flatDto : categoryFlatList) {
        CategoryTreeDto node = idToNodeMap.get(flatDto.getId());
        if (flatDto.getParentId() == null) {
          rootList.add(node);
        } else {
          CategoryTreeDto parent = idToNodeMap.get(flatDto.getParentId());
          if (parent != null) {
            parent.getChildren().add(node);
          } else {
            System.out.println("⚠ 잘못된 parentId: " + flatDto.getParentId());
          }
        }
      }

      //
      return rootList;
    }
  }

  public static void printTree(List<CategoryTreeDto> treeList, int depth) {
    for (CategoryTreeDto node : treeList) {
      System.out.println("  ".repeat(depth) + "- " + node.getName());
      printTree(node.getChildren(), depth + 1);
    }
  }


  public static void main(String[] args) {
    List<CategoryFlatDto> categoryFlatList = Arrays.asList(
        new CategoryFlatDto(1L, "전자제품", null),
        new CategoryFlatDto(2L, "컴퓨터", 1L),
        new CategoryFlatDto(3L, "노트북", 2L),
        new CategoryFlatDto(4L, "데스크탑", 2L),
        new CategoryFlatDto(5L, "가전", 1L),
        new CategoryFlatDto(6L, "냉장고", 5L),
        new CategoryFlatDto(7L, "에어컨", 5L),
        new CategoryFlatDto(8L, "패션", null),
        new CategoryFlatDto(9L, "남성", 8L),
        new CategoryFlatDto(10L, "여성", 8L)
    );

    // 트리 변환 및 출력
    CategoryConverter converter = new CategoryConverter();
    List<CategoryTreeDto> tree = converter.convertToTree(categoryFlatList);

    printTree(tree, 0);
  }
}
