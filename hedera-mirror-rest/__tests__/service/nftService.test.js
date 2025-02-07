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

'use strict';

const {NftService} = require('../../service');
const {assertSqlQueryEqual} = require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

const {defaultMochaStatements} = require('./defaultMochaStatements');
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

describe('NftService.getNftsFiltersQuery tests', () => {
  const orderClause = 'order by token_id asc, serial_number asc';
  test('Verify simple query', async () => {
    const [query, params] = NftService.getNftsFiltersQuery(['account_id = $1'], [2], orderClause, 5);
    const expected = `select account_id,created_timestamp,delegating_spender,deleted,metadata,modified_timestamp,serial_number,spender,token_id
        from nft
        where account_id = $1
        order by token_id asc, serial_number asc
        limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const additionalConditions = ['account_id = $1', 'token_id > $2', 'serial_number = $3'];
    const [query, params] = NftService.getNftsFiltersQuery(additionalConditions, [2, 10, 20], orderClause, 5);
    const expected = `select account_id,created_timestamp,delegating_spender,deleted,metadata,modified_timestamp,serial_number,spender,token_id
        from nft
        where account_id = $1
          and token_id > $2
          and serial_number = $3
        order by token_id asc, serial_number asc
        limit $4`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 20, 5]);
  });
});
