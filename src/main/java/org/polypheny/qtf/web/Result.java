/*
 * Copyright 2019-2021 The Polypheny Project
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


import java.util.Arrays;


public class Result {

    public static class DbColumn {

        public String name;
        public String dataType;
        public boolean primary;
    }


    public String table;
    public DbColumn[] header;
    public String[][] data;
    public String error;
    public Integer affectedRows;
    public String generatedQuery;

    public Result( String error ) {
        this.error = error;
    }

    public boolean containsColumn( String columnName ) {
        final String test;
        if ( columnName.contains( "." ) ) {
            test = columnName.substring( 0, columnName.lastIndexOf( "." ) );
        } else {
            test = columnName;
        }
        return Arrays.stream( header ).filter( h -> h.name.equals( test ) ).count() == 1;
    }

}
