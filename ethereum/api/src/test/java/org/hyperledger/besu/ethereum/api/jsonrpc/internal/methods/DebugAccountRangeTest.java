/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebugAccountRangeTest {

  private static final Hash NON_CANONICAL_BLOCK_HASH = Hash.fromHexString("0x" + "01".repeat(32));

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata;
  @Mock private BlockHeader blockHeader;

  @Test
  void shouldLoadWorldStateByRequestedBlockHash() {
    when(blockchainQueries.blockByHash(NON_CANONICAL_BLOCK_HASH))
        .thenReturn(Optional.of(blockWithMetadata));
    when(blockWithMetadata.getHeader()).thenReturn(blockHeader);

    final JsonRpcRequestContext requestContext =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "debug_accountRange",
                new Object[] {NON_CANONICAL_BLOCK_HASH.toString(), false, "0x00", 10}));

    new DebugAccountRange(blockchainQueries).response(requestContext);

    verify(blockchainQueries).getAndMapWorldState(eq(NON_CANONICAL_BLOCK_HASH), any());
  }
}
