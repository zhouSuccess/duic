/**
 * Copyright 2017-2018 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zhudy.duic.web.security

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTDecodeException
import io.zhudy.duic.BizCode
import io.zhudy.duic.BizCodeException
import io.zhudy.duic.Config
import io.zhudy.duic.UserContext
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * @author Kevin Zou (kevinz@weghst.com)
 */
class AuthorizedHandlerFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        if (request.uri().path.endsWith("/api/admins/login")) {
            return next.handle(request)
        }

        val token = request.cookies().getFirst("token")?.value
        if (token.isNullOrEmpty()) {
            throw BizCodeException(BizCode.Classic.C_401, "缺少 token")
        }

        val jwt = try {
            JWT.decode(token)
        } catch (e: JWTDecodeException) {
            throw BizCodeException(BizCode.Classic.C_401, "解析 token 失败 ${e.message}")
        }

        if ((jwt.expiresAt?.time ?: 0) < System.currentTimeMillis()) {
            throw BizCodeException(BizCode.Classic.C_401, "token 已经过期请重新登录")
        }

        if (jwt.id.isNullOrEmpty()) {
            throw BizCodeException(BizCode.Classic.C_401, "错误的 token")
        }

        request.attributes()[UserContext.CONTEXT_KEY] = object : UserContext {
            override val email: String
                get() = jwt.id
            override val isRoot: Boolean
                get() = Config.rootEmail == email
        }

        return next.handle(request)
    }
}