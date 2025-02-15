package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.util.Utility;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer")
public class MirrorProperties {

    @NotNull
    private Path dataPath = Paths.get(".", "data");

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    private boolean importHistoricalAccountInfo = true;

    private Path initialAddressBook;

    private boolean leaderElection = false;

    @NotNull
    private HederaNetwork network = HederaNetwork.DEMO;

    @Min(0)
    private long shard = 0L;

    private Instant startDate;

    private Long startBlockNumber;

    private Long topicRunningHashV2AddedTimestamp;

    @NotNull
    private Instant verifyHashAfter = Instant.EPOCH;

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        DEMO("hedera-demo-streams"),
        MAINNET("hedera-mainnet-streams"),
        PREVIEWNET("hedera-preview-testnet-streams"),
        OTHER(""), // Pre-prod or ad hoc environments
        TESTNET("hedera-stable-testnet-streams-2020-08-27");

        private final String bucketName;

        public boolean isAllowAnonymousAccess() {
            return this == DEMO;
        }
    }
}
