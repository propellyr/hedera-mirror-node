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

const _ = require('lodash');

class CryptoAllowance {
  /**
   * Parses crypto_allowance table columns into object
   */
  constructor(cryptoAllowance) {
    Object.assign(
      this,
      _.mapKeys(cryptoAllowance, (v, k) => _.camelCase(k))
    );
  }

  static historyTableName = 'crypto_allowance_history';
  static tableAlias = 'ca';
  static tableName = 'crypto_allowance';

  static AMOUNT = 'amount';
  static OWNER = 'owner';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SPENDER = 'spender';
  static TIMESTAMP_RANGE = 'timestamp_range';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

module.exports = CryptoAllowance;
