package com.estudos.users_api.dto.pagination

import com.fasterxml.jackson.annotation.JsonProperty

data class PageResponse<T>(
    @JsonProperty("result_set")
    val resultSet: PageResult,

    val items: List<T>,

    @JsonProperty("_links")
    val links: PageLinks

)
