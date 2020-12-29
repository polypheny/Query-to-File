/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.qtf.web;


/**
 * Models for Websocket requests
 */
public abstract class UIRequest {

    public final String requestType;

    public UIRequest( String requestType ) {
        this.requestType = requestType;
    }

    public static class TableRequest extends UIRequest {

        private final String tableId;

        public TableRequest( String tableId ) {
            super( "TableRequest" );
            this.tableId = tableId;
        }

    }


    public static class QueryRequest extends UIRequest {

        String query;
        private final boolean analyze = false;

        public QueryRequest( String query ) {
            super( "QueryRequest" );
            this.query = query;
        }

    }

}
