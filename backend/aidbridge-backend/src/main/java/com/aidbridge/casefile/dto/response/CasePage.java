package com.aidbridge.casefile.dto.response;
import java.util.List;
public record CasePage(List<CaseSummaryResponse> items, int page, int size, long totalElements) { }
