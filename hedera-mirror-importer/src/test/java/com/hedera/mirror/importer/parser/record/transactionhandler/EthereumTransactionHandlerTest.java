package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

class EthereumTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock(lenient = true)
    protected EthereumTransactionParser ethereumTransactionParser;

    @Override
    protected TransactionHandler getTransactionHandler() {
        doReturn(domainBuilder.ethereumTransaction().get()).when(ethereumTransactionParser).parse(any());
        doReturn(recordItemBuilder.bytes(32).toByteArray()).when(ethereumTransactionParser).retrievePublicKey(any());
        return new EthereumTransactionHandler(entityIdService, new EntityProperties(), entityListener,
                ethereumTransactionParser);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setEthereumTransaction(EthereumTransactionBody.newBuilder().build());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setConsensusTimestamp(MODIFIED_TIMESTAMP)
                .setReceipt(getTransactionReceipt(ResponseCodeEnum.SUCCESS))
                .setContractCallResult(recordItemBuilder.contractFunctionResult(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void testGetEntityIdOnCreate() {
        var recordItem = recordItemBuilder.ethereumTransaction(true, contractId).build();
        ContractID contractId = recordItem.getRecord().getContractCreateResult().getContractID();
        EntityId expectedEntityId = EntityId.of(contractId);

        when(entityIdService.lookup(contractId)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void testGetEntityIdOnCall() {
        var recordItem = recordItemBuilder.ethereumTransaction(false, contractId).build();
        ContractID contractId = recordItem.getRecord().getContractCallResult().getContractID();
        EntityId expectedEntityId = EntityId.of(contractId);

        when(entityIdService.lookup(contractId)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }
}
