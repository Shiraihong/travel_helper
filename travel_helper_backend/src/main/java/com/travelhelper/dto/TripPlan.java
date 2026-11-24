package com.travelhelper.dto;

import java.util.List;

/** 结构化输出示例的目标类型：一段行程的结构。 */
public record TripPlan(String destination, Integer days, List<String> attractions) {
}