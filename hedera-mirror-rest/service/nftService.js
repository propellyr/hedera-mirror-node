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

const {Nft} = require('../model');
const BaseService = require('./baseService');
const {OrderSpec} = require('../sql');

/**
 * Nft business model
 */
class NftService extends BaseService {
  static nftByIdQuery = 'select * from nft where token_id = $1 and serial_number = $2';

  static nftQuery = `select
    ${Nft.ACCOUNT_ID},
    ${Nft.CREATED_TIMESTAMP},
    ${Nft.DELEGATING_SPENDER},
    ${Nft.DELETED},
    ${Nft.METADATA},
    ${Nft.MODIFIED_TIMESTAMP},
    ${Nft.SERIAL_NUMBER},
    ${Nft.SPENDER},
    ${Nft.TOKEN_ID}
    from ${Nft.tableName}`;

  async getNft(tokenId, serialNumber) {
    const {rows} = await pool.queryQuietly(NftService.nftByIdQuery, [tokenId, serialNumber]);
    return _.isEmpty(rows) ? null : new Nft(rows[0]);
  }

  getNftsFiltersQuery(whereConditions, whereParams, orderClause, limit, paramsLength = whereParams.length) {
    const params = whereParams;
    const query = [
      NftService.nftQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      orderClause,
      super.getLimitQuery(paramsLength + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  async getNftOwnership(lower, inner, upper, order, limit) {
    let allParams = [];
    let allQueries = [];
    const orderClause = super.getOrderByQuery(
      OrderSpec.from(Nft.TOKEN_ID, order),
      OrderSpec.from(Nft.SERIAL_NUMBER, order)
    );
    if (!_.isEmpty(lower)) {
      const [lowerQuery, lowerParams] = this.getNftsFiltersQuery(lower.conditions, lower.params, orderClause, limit);
      allQueries = allQueries.concat(`(${lowerQuery})`);
      allParams = allParams.concat(lowerParams);
    }

    if (!_.isEmpty(inner)) {
      const [innerQuery, innerParams] = this.getNftsFiltersQuery(
        inner.conditions,
        inner.params,
        orderClause,
        limit,
        inner.params.length + allParams.length
      );
      allQueries = allQueries.concat(`(${innerQuery})`);
      allParams = allParams.concat(innerParams);
    }

    if (!_.isEmpty(upper)) {
      const [upperQuery, upperParams] = this.getNftsFiltersQuery(
        upper.conditions,
        upper.params,
        orderClause,
        limit,
        upper.params.length + allParams.length
      );
      allQueries = allQueries.concat(`(${upperQuery})`);
      allParams = allParams.concat(upperParams);
    }

    let unionQuery = allQueries.filter((x) => !!x).join('\nunion all\n');

    // if more than 1 query was combined add an additional order and limit to format joined results
    if (allQueries.length > 1) {
      unionQuery = [unionQuery, orderClause, super.getLimitQuery(allParams.length)].join('\n');
    }

    const rows = await super.getRows(unionQuery, allParams, 'getNftOwnership');
    return rows.map((nft) => new Nft(nft));
  }
}

module.exports = new NftService();
