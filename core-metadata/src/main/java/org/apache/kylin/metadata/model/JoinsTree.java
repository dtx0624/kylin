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

package org.apache.kylin.metadata.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;

public class JoinsTree {
    
    final private Chain root;
    final private Map<TableRef, Chain> tableChains = new LinkedHashMap<>();

    public JoinsTree(TableRef rootTable, List<JoinDesc> joins) {
        for (JoinDesc join : joins) {
            for (TblColRef col : join.getForeignKeyColumns())
                Preconditions.checkState(col.isQualified());
            for (TblColRef col : join.getPrimaryKeyColumns())
                Preconditions.checkState(col.isQualified());
        }
        
        root = new Chain(rootTable, null, null);
        tableChains.put(rootTable, root);
        
        for (JoinDesc join : joins) {
            TableRef pkSide = join.getPKSide();
            Chain fkSide = tableChains.get(join.getFKSide());
            tableChains.put(pkSide, new Chain(pkSide, join, fkSide));
        }
    }
    
    public Map<String, String> matches(JoinsTree another) {
        Map<String, String> matchUp = new HashMap<>();
        
        for (Chain chain : tableChains.values()) {
            if (matchInTree(chain, another, matchUp) == false)
                return null;
        }
        
        return matchUp;
    }
    
    private boolean matchInTree(Chain chain, JoinsTree another, Map<String, String> matchUp) {
        String thisAlias = chain.table.getAlias();
        if (matchUp.containsKey(thisAlias))
            return true;
        
        for (Chain anotherChain : another.tableChains.values()) {
            if (matchChain(chain, anotherChain, matchUp)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchChain(Chain chain, Chain anotherChain, Map<String, String> matchUp) {
        String thisAlias = chain.table.getAlias();
        String anotherAlias = anotherChain.table.getAlias();
        
        String curMatch = matchUp.get(thisAlias);
        if (curMatch != null)
            return curMatch.equals(anotherAlias);
        if (curMatch == null && matchUp.values().contains(anotherAlias))
            return false;
        
        boolean matches = false;
        if (chain.join == null) {
            matches = anotherChain.join == null && chain.table.getTableDesc().equals(anotherChain.table.getTableDesc());
        } else {
            matches = chain.join.matches(anotherChain.join) && matchChain(chain.fkSide, anotherChain.fkSide, matchUp);
        }
        
        if (matches) {
            matchUp.put(thisAlias, anotherAlias);
        }
        return matches;
    }

    private static class Chain {
        TableRef table; // pk side
        JoinDesc join;
        Chain fkSide;
        
        public Chain(TableRef table, JoinDesc join, Chain fkSide) {
            this.table = table;
            this.join = join;
            this.fkSide = fkSide;
            if (join != null) {
                Preconditions.checkArgument(table == join.getPKSide());
                Preconditions.checkArgument(fkSide.table == join.getPKSide());
            }
        }
    }
}
