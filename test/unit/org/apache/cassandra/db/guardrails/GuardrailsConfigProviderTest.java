/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.db.guardrails;

import org.junit.Test;

import org.apache.cassandra.config.GuardrailsOptions;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.service.ClientState;
import org.assertj.core.api.Assertions;

import static java.lang.String.format;

public class GuardrailsConfigProviderTest extends GuardrailTester
{
    @Test
    public void testBuildCustom() throws Throwable
    {
        String name = getClass().getCanonicalName() + '$' + CustomProvider.class.getSimpleName();
        GuardrailsConfigProvider provider = GuardrailsConfigProvider.build(name);
        Threshold guard = new Threshold(state -> provider.getOrCreate(state).getTables().getWarnThreshold(),
                                        state -> provider.getOrCreate(state).getTables().getAbortThreshold(),
                                        (isWarn, what, v, t) -> format("%s: for %s, %s > %s",
                                                                       isWarn ? "Warning" : "Aborting", what, v, t));

        assertValid(() -> guard.guard(5, "Z", userClientState));
        assertWarns(() -> guard.guard(25, "A", userClientState), "Warning: for A, 25 > 10");
        assertWarns(() -> guard.guard(100, "B", userClientState), "Warning: for B, 100 > 10");
        assertAborts(() -> guard.guard(101, "X", userClientState), "Aborting: for X, 101 > 100");
        assertAborts(() -> guard.guard(200, "Y", userClientState), "Aborting: for Y, 200 > 100");
        assertValid(() -> guard.guard(5, "Z", userClientState));

        Assertions.assertThatThrownBy(() -> GuardrailsConfigProvider.build("unexistent_class"))
                  .isInstanceOf(ConfigurationException.class)
                  .hasMessageContaining("Unable to find custom guardrails config provider class 'unexistent_class'");
    }

    /**
     * Custom {@link GuardrailsConfigProvider} implementation that simply duplicates the threshold values.
     */
    public static class CustomProvider extends GuardrailsConfigProvider.Default
    {
        public GuardrailsConfig getOrCreate(ClientState state)
        {
            return new CustomConfig();
        }
    }

    public static class CustomConfig extends GuardrailsOptions
    {
        private final IntThreshold tables = new IntThreshold();

        public CustomConfig()
        {
            tables.setThresholds(10, 100);
        }

        @Override
        public IntThreshold getTables()
        {
            return tables;
        }
    }
}
