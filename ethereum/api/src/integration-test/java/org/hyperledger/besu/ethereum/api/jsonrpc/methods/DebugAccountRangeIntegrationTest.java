/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.DebugAccountRange;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugAccountRangeAtResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.worldstate.MutableWorldState;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

class DebugAccountRangeIntegrationTest {

  private static final Address FORK_ONLY_ACCOUNT =
      Address.fromHexString("0x0000000000000000000000000000000000000002");

  @Test
  void returnsAccountsForNonCanonicalBlockHashWithForest() throws Exception {
    final String genesisJson =
        Resources.toString(BlockTestUtil.getTestGenesisUrl(), StandardCharsets.UTF_8);
    final BlockchainImporter importer =
        new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson);

    final MutableBlockchain blockchain = createInMemoryBlockchain(importer.getGenesisBlock());
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    importer.getGenesisState().writeStateTo(worldStateArchive.getWorldState());
    final ProtocolContext context =
        new ProtocolContext.Builder()
            .withBlockchain(blockchain)
            .withWorldStateArchive(worldStateArchive)
            .build();

    for (final Block block : importer.getBlocks()) {
      final ProtocolSpec protocolSpec =
          importer.getProtocolSchedule().getByBlockHeader(block.getHeader());
      protocolSpec.getBlockImporter().importBlock(context, block, HeaderValidationMode.FULL);
    }

    final Block canonicalBlock = importer.getBlocks().get(1);
    final MutableWorldState forkWorldState = worldStateArchive.getWorldState();
    final WorldUpdater updater = forkWorldState.updater();
    updater.createAccount(FORK_ONLY_ACCOUNT).setBalance(Wei.of(42));
    updater.commit();
    forkWorldState.persist(null);

    final BlockHeader nonCanonicalHeader =
        BlockHeaderBuilder.fromHeader(canonicalBlock.getHeader())
            .coinbase(Address.fromHexString("0x0000000000000000000000000000000000000001"))
            .stateRoot(forkWorldState.rootHash())
            .blockHeaderFunctions(
                ScheduleBasedBlockHeaderFunctions.create(importer.getProtocolSchedule()))
            .buildBlockHeader();
    final Block nonCanonicalBlock = new Block(nonCanonicalHeader, canonicalBlock.getBody());
    blockchain.storeBlock(
        nonCanonicalBlock, blockchain.getTxReceipts(canonicalBlock.getHash()).orElse(List.of()));

    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            importer.getProtocolSchedule(),
            blockchain,
            worldStateArchive,
            MiningConfiguration.newDefault().setCoinbase(Address.ZERO));
    final DebugAccountRange method = new DebugAccountRange(blockchainQueries);

    final Map<String, String> canonicalAccounts = addressMap(method, canonicalBlock.getHash());
    final Map<String, String> nonCanonicalAccounts =
        addressMap(method, nonCanonicalHeader.getHash());

    assertThat(nonCanonicalAccounts).containsValue(FORK_ONLY_ACCOUNT.toString());
    assertThat(canonicalAccounts).doesNotContainValue(FORK_ONLY_ACCOUNT.toString());
  }

  private Map<String, String> addressMap(final DebugAccountRange method, final Hash blockHash) {
    final JsonRpcResponse response =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    "debug_accountRange",
                    new Object[] {blockHash.toString(), false, "0x00", 10_000})));
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    return ((DebugAccountRangeAtResult) ((JsonRpcSuccessResponse) response).getResult())
        .getAddressMap();
  }
}
