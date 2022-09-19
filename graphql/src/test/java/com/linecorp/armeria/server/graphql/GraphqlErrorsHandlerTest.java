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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linecorp.armeria.client.BlockingWebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.annotation.Nullable;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorException;
import graphql.schema.DataFetcher;

public class GraphqlErrorsHandlerTest {

    @RegisterExtension
    static ServerExtension server = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            final File graphqlSchemaFile =
                    new File(getClass().getResource("/test.graphqls").toURI());

            final GraphqlErrorsHandler errorsHandler = new GraphqlErrorsHandler() {
                @Override
                public @Nullable HttpResponse handle(ServiceRequestContext ctx, ExecutionInput input,
                                                     ExecutionResult result, MediaType negotiatedProduceType,
                                                     @Nullable Throwable cause) {
                    final List<GraphQLError> errors = result.getErrors();
                    if (errors.stream().map(GraphQLError::getMessage).anyMatch(m -> m.endsWith("foo"))) {
                        return HttpResponse.ofJson(HttpStatus.OK);
                    }
                    return null;
                }
            };

            final GraphqlService service =
                    GraphqlService.builder()
                                  .schemaFile(graphqlSchemaFile)
                                  .runtimeWiring(c -> {
                                      final DataFetcher<String> foo = dataFetcher("foo");
                                      c.type("Query",
                                             typeWiring -> typeWiring.dataFetcher("foo", foo));
                                      final DataFetcher<String> error = dataFetcher("error");
                                      c.type("Query",
                                             typeWiring -> typeWiring.dataFetcher("error", error));
                                  })
                                  .errorsHandler(errorsHandler)
                                  .build();
            sb.service("/graphql", service);
        }
    };

    private static DataFetcher<String> dataFetcher(String value) {
        return environment -> {
            final ServiceRequestContext ctx = GraphqlServiceContexts.get(environment);
            assertThat(ctx.eventLoop().inEventLoop()).isTrue();
            // Make sure that a ServiceRequestContext is available
            assertThat(ServiceRequestContext.current()).isSameAs(ctx);
            throw GraphqlErrorException.newErrorException().message(value).build();
        };
    }

    @Test
    void shouldHandleFooError() {
        final HttpRequest request = HttpRequest.builder().post("/graphql")
                                               .content(MediaType.GRAPHQL, "{foo}")
                                               .build();
        final AggregatedHttpResponse response = BlockingWebClient.of(server.httpUri())
                                                                 .execute(request);
        assertThat(response.status()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldHandleUnknownError() {
        final HttpRequest request = HttpRequest.builder().post("/graphql")
                                               .content(MediaType.GRAPHQL, "{error}")
                                               .build();
        final AggregatedHttpResponse response = BlockingWebClient.of(server.httpUri())
                                                                 .execute(request);
        assertThat(response.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
