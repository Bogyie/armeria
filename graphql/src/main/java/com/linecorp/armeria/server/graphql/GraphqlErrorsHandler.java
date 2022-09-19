/*
 * Copyright 2022 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.graphql;

import static java.util.Objects.requireNonNull;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.server.ServiceRequestContext;

import graphql.ExecutionInput;
import graphql.ExecutionResult;

/**
 * A handler that maps GraphQL errors or a {@link Throwable} to an {@link HttpResponse}.
 */
@UnstableApi
@FunctionalInterface
public interface GraphqlErrorsHandler {

    /**
     * Maps the {@link ServiceRequestContext}, {@link ExecutionInput}, {@link ExecutionResult},
     * {@link MediaType}, {@link Throwable} to the {@link HttpResponse}.
     */
    @Nullable
    HttpResponse handle(ServiceRequestContext ctx, ExecutionInput input,
                        ExecutionResult result, MediaType negotiatedProduceType, @Nullable Throwable cause);

    /**
     * Returns a composed {@link GraphqlErrorsHandler} that applies this first and the specified
     * other later if this returns null.
     */
    default GraphqlErrorsHandler orElse(GraphqlErrorsHandler other) {
        requireNonNull(other, "other");
        if (this == other) {
            return this;
        }
        return (ctx, input, produceType, executionResult, cause) -> {
            final HttpResponse response = handle(ctx, input, produceType, executionResult, cause);
            if (response != null) {
                return response;
            }
            return other.handle(ctx, input, produceType, executionResult, cause);
        };
    }
}
