package com.hedera.mirror.importer.domain;

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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class ContractResultServiceImpl implements ContractResultService {

    private final EntityProperties entityProperties;
    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final TransactionHandlerFactory transactionHandlerFactory;

    @Override
    public void process(@NonNull RecordItem recordItem, Transaction transaction) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        var transactionRecord = recordItem.getRecord();
        var functionResult = transactionRecord.hasContractCreateResult() ?
                transactionRecord.getContractCreateResult() : transactionRecord.getContractCallResult();

        // handle non create/call transactions
        if (!isContractCreateOrCall(recordItem
                .getTransactionBody()) && !isValidContractFunctionResult(functionResult)) {
            // if transaction is neither a create/call and has no valid ContractFunctionResult then skip
            return;
        }

        // contractResult
        TransactionHandler transactionHandler = transactionHandlerFactory
                .get(TransactionType.of(transaction.getType()));

        // in pre-compile case transaction is not a contract type and entityId will be of a different type
        var contractId = isContractCreateOrCall(recordItem.getTransactionBody()) ? transaction.getEntityId() :
                entityIdService.lookup(functionResult.getContractID());
        processContractResult(recordItem, contractId, functionResult, transactionHandler);
    }

    private boolean isValidContractFunctionResult(ContractFunctionResult contractFunctionResult) {
        return !contractFunctionResult.equals(ContractFunctionResult.getDefaultInstance());
    }

    private boolean isContractCreateOrCall(TransactionBody transactionBody) {
        return transactionBody.hasContractCall() || transactionBody.hasContractCreateInstance();
    }

    private void processContractResult(RecordItem recordItem, EntityId contractEntityId,
                                       ContractFunctionResult functionResult,
                                       TransactionHandler transactionHandler) {
        // create child contracts regardless of contractResults support
        List<Long> contractIds = getCreatedContractIds(functionResult, recordItem, contractEntityId);
        if (!entityProperties.getPersist().isContractResults()) {
            return;
        }

        ContractResult contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        contractResult.setContractId(contractEntityId);
        contractResult.setPayerAccountId(recordItem.getPayerAccountId());
        transactionHandler.updateContractResult(contractResult, recordItem);

        if (isValidContractFunctionResult(functionResult)) {
            if (!isContractCreateOrCall(recordItem.getTransactionBody())) {
                // amount, gasLimit and functionParameters were missing from record proto prior to HAPI v0.25
                contractResult.setAmount(functionResult.getAmount());
                contractResult.setGasLimit(functionResult.getGas());
                contractResult.setFunctionParameters(DomainUtils.toBytes(functionResult.getFunctionParameters()));
            }

            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setCreatedContractIds(contractIds);
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());

            if (functionResult.hasSenderId()) {
                contractResult.setSenderId(EntityId.of(functionResult.getSenderId()));
            }

            processContractLogs(functionResult, contractResult);
            processContractStateChanges(functionResult, contractResult);
        }

        entityListener.onContractResult(contractResult);
    }

    private void processContractLogs(ContractFunctionResult functionResult, ContractResult contractResult) {
        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);

            ContractLog contractLog = new ContractLog();
            contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
            contractLog.setContractId(lookup(contractResult.getContractId(), contractLoginfo.getContractID()));
            contractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
            contractLog.setIndex(index);
            contractLog.setRootContractId(contractResult.getContractId());
            contractLog.setPayerAccountId(contractResult.getPayerAccountId());
            contractLog.setTopic0(Utility.getTopic(contractLoginfo, 0));
            contractLog.setTopic1(Utility.getTopic(contractLoginfo, 1));
            contractLog.setTopic2(Utility.getTopic(contractLoginfo, 2));
            contractLog.setTopic3(Utility.getTopic(contractLoginfo, 3));
            entityListener.onContractLog(contractLog);
        }
    }

    private void processContractStateChanges(ContractFunctionResult functionResult, ContractResult contractResult) {

        for (var stateChange : functionResult.getStateChangesList()) {
            var contractId = lookup(contractResult.getContractId(), stateChange.getContractID());
            for (var storageChange : stateChange.getStorageChangesList()) {
                ContractStateChange contractStateChange = new ContractStateChange();
                contractStateChange.setConsensusTimestamp(contractResult.getConsensusTimestamp());
                contractStateChange.setContractId(contractId);
                contractStateChange.setPayerAccountId(contractResult.getPayerAccountId());
                contractStateChange.setSlot(DomainUtils.toBytes(storageChange.getSlot()));
                contractStateChange.setValueRead(DomainUtils.toBytes(storageChange.getValueRead()));

                // If a value of zero is written the valueWritten will be present but the inner value will be
                // absent. If a value was read and not written this value will not be present.
                if (storageChange.hasValueWritten()) {
                    contractStateChange
                            .setValueWritten(DomainUtils.toBytes(storageChange.getValueWritten().getValue()));
                }

                entityListener.onContractStateChange(contractStateChange);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private List<Long> getCreatedContractIds(ContractFunctionResult functionResult, RecordItem recordItem,
                                             EntityId parentEntityContractId) {
        List<Long> createdContractIds = new ArrayList<>();
        boolean persist = shouldPersistCreatedContractIDs(recordItem);
        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = entityIdService.lookup(createdContractId);
            if (!EntityId.isEmpty(contractId)) {
                createdContractIds.add(contractId.getId());
                // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
                if (persist && !contractId.equals(parentEntityContractId)) {
                    processCreatedContractEntity(recordItem, contractId);
                }
            }
        }

        return createdContractIds;
    }

    private void processCreatedContractEntity(RecordItem recordItem, EntityId contractEntityId) {
        Contract contract = contractEntityId.toEntity();
        contract.setCreatedTimestamp(recordItem.getConsensusTimestamp());
        contract.setDeleted(false);
        contract.setMaxAutomaticTokenAssociations(0);
        contract.setTimestampLower(recordItem.getConsensusTimestamp());

        if (recordItem.getTransactionBody().hasContractCreateInstance()) {
            updateContractEntityOnCreate(contract, recordItem);
        }

        entityListener.onContract(contract);
    }

    /**
     * Updates the contract entities in ContractCreateResult.CreatedContractIDs list. The method should only be called
     * for such contract entities in pre services 0.23 contract create transactions. Since services 0.23, the child
     * contract creation is externalized into its own synthesized contract create transaction and should be processed by
     * ContractCreateTransactionHandler.
     *
     * @param contract The contract entity
     * @param recordItem The recordItem in which the contract is created
     */
    private void updateContractEntityOnCreate(Contract contract, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractCreateInstance();

        if (transactionBody.hasAutoRenewPeriod()) {
            contract.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            contract.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasProxyAccountID()) {
            contract.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        if (transactionBody.hasFileID()) {
            contract.setFileId(EntityId.of(transactionBody.getFileID()));
        }

        contract.setMemo(transactionBody.getMemo());
    }

    /**
     * Persist contract entities in createdContractIDs if it's prior to HAPI 0.23.0. After that the createdContractIDs
     * list is also externalized as contract create child records so we only need to persist the complete contract
     * entity from the child record.
     *
     * @param recordItem to check
     * @return Whether the createdContractIDs list should be persisted.
     */
    private boolean shouldPersistCreatedContractIDs(RecordItem recordItem) {
        return recordItem.isSuccessful() && entityProperties.getPersist().isContracts() &&
                recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0);
    }

    /**
     * This method works around a services issue that occurs when events emitted by a CREATE2 contract produce an
     * invalid ContractID. The EVM generated logs contain the 20 byte CREATE2 EVM address and services does not properly
     * convert this back to the 'shard.realm.num' format that should always be present in the record. A similar issue
     * occurs for state changes.
     * <p>
     * We work around this issue by converting the invalid 'shard.realm.num' format back to an EVM address and look it
     * up in the database. If the invalid contract ID is produced by a CREATE2 constructor invocation, it won't be
     * present in the database yet and our only recourse is to fall back to using the root contract ID.
     * <p>
     * This issue never made it to mainnet, so this code should be deleted after testnet is reset.
     *
     * @param rootContractId The contract create or call that initiated the transaction.
     * @param contractId     The contract ID that appears somewhere in the ContractFunctionResult.
     * @return The converted entity ID.
     */
    private EntityId lookup(EntityId rootContractId, ContractID contractId) {
        try {
            // We won't always get a negative or very large number to cause an InvalidEntityException
            if (contractId.getShardNum() != 0 || contractId.getRealmNum() != 0) {
                return fallbackLookup(rootContractId, contractId);
            }
            return entityIdService.lookup(contractId);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InvalidEntityException) {
                return fallbackLookup(rootContractId, contractId);
            }
            throw e;
        }
    }

    private EntityId fallbackLookup(EntityId rootContractId, ContractID contractId) {
        byte[] evmAddress = DomainUtils.toEvmAddress(contractId);
        log.warn("Invalid ContractID {}.{}.{}. Attempting conversion to EVM address: {}",
                contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum(),
                Hex.encodeHexString(evmAddress));

        var evmAddressId = ContractID.newBuilder()
                .setShardNum(rootContractId.getShardNum())
                .setRealmNum(rootContractId.getRealmNum())
                .setEvmAddress(DomainUtils.fromBytes(evmAddress))
                .build();

        var entityId = entityIdService.lookup(evmAddressId);
        return !EntityId.isEmpty(entityId) ? entityId : rootContractId;
    }
}
