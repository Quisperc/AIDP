package cn.civer.authserver.dto;

import java.util.List;

/**
 * 统一分页结果。
 *
 * 为什么自带 totalPages / hasPrevious / hasNext 而不是只给总数：
 * 模板里做 `page + 1 < totalPages` 这种算术，Thymeleaf 一写长就难读，
 * 而且 0 基 / 1 基的换算散落在各处最容易出错。把「第几页（0 基）」
 * 和「显示第几页（1 基）」分开，模板只负责取值。
 *
 * 用 record 而不是 class：DTO 没有行为，字段即契约。
 */
public record PageResult<T>(
		List<T> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean hasPrevious,
		boolean hasNext) {

	/** 列表页统一每页 20 条：数据量在百级，翻页只为避免一次渲染过长 */
	public static final int DEFAULT_SIZE = 20;

	public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
		int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
		boolean hasPrevious = page > 0;
		boolean hasNext = page + 1 < totalPages;
		return new PageResult<>(items, page, size, totalElements, totalPages, hasPrevious, hasNext);
	}

	/** 供模板显示的 1 基页码 */
	public int displayPage() {
		return page + 1;
	}

	public static <T> PageResult<T> empty(int size) {
		return of(List.of(), 0, size, 0);
	}
}
