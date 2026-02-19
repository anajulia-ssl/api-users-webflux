package com.estudos.users_api.dto.pagination

import com.estudos.users_api.dto.PageQuery
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import kotlin.math.max

object Paginator {

    fun <T : Any, R : Any> build(
        query: PageQuery,
        contentMono: Mono<List<T>>,
        totalMono: Mono<Long>,
        request: ServerHttpRequest,
        mapper: (T) -> R
    ): Mono<PageResponse<R>> {
        return Mono.zip(contentMono, totalMono)
            .map { tuple ->
                val content = tuple.t1
                val total = tuple.t2

                val limit = query.limit
                val offset = query.offset
                val size = content.size

                val links = createLinks(request, offset, limit, total)

                PageResponse(
                    resultSet = PageResult(
                        limit = limit,
                        offset = offset,
                        total = total,
                        size = size
                    ),
                    items = content.map(mapper),
                    links = links
                )
            }
    }

    /* ------------------ helpers ------------------- */

    private fun createLinks(
        request: ServerHttpRequest,
        offset: Int,
        limit: Int,
        total: Long
    ): PageLinks {
        val base = UriComponentsBuilder.fromUri(request.uri)

        val first = link(base.cloneBuilder(), 0, limit)
        val self  = link(base.cloneBuilder(), offset, limit)

        val lastOffset = if (total <= 0) 0 else ((total - 1) / limit).toInt() * limit
        val last  = link(base.cloneBuilder(), lastOffset, limit)

        val prev  = if (offset > 0) link(base.cloneBuilder(), max(0, offset - limit), limit) else null
        val next  = if (offset + limit < total) link(base.cloneBuilder(), offset + limit, limit) else null

        return PageLinks(first = first, self = self, last = last, prev = prev, next = next)
    }

    private fun link(builder: UriComponentsBuilder, offset: Int, limit: Int): PageLink =
        PageLink(
            builder
                .replaceQueryParam("offset", offset)
                .replaceQueryParam("limit", limit)
                .build(true)
                .toUriString()
        )
}