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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.polypheny.qtf.fuse.ResultFS.Operation;
import org.polypheny.qtf.fuse.ResultFS.ResultFile;
import org.polypheny.qtf.web.Result.DbColumn;


public class BatchUpdateRequest {

    final String tableId;
    final transient Result result;
    @Getter
    final ArrayList<Update> updates = new ArrayList<>();


    public BatchUpdateRequest( Result result ) {
        this.tableId = result.table;
        this.result = result;
    }

    public void addUpdate( Update update ) {
        this.updates.add( update );
    }

    public class Update {

        final Map<String, String> oldPkValues = new HashMap<>();
        @Getter
        final Map<String, Value> newValues = new HashMap<>();

        public Update( int ithRow ) {
            if ( ithRow < 0 || ithRow >= result.data.length ) {
                throw new IllegalArgumentException( "The ith row " + ithRow + " does not exist in the resultSet." );
            }
            for ( int j = 0; j < result.header.length; j++ ) {
                DbColumn col = result.header[j];
                if ( col.primary ) {
                    oldPkValues.put( col.name, result.data[ithRow][j] );
                }
            }
        }

        public void addValue( ResultFile file ) {
            String key = file.getName();
            if ( key.contains( "." ) ) {
                key = file.getName().substring( 0, file.getName().lastIndexOf( "." ) );
            }
            if ( file.getLastOp() == Operation.UNLINK ) {
                if ( newValues.containsKey( key ) ) {
                    //e.g. if deleting img.png and then adding img.jpeg
                    return;
                }
                newValues.put( key, new Value( null, null ) );
            } else {
                if ( file.getLastOp() == null && file.getDataAsString() != null ) {
                    newValues.put( key, new Value( file.getDataAsString(), null ) );
                } else {
                    newValues.put( key, new Value( null, file ) );
                }
            }
        }
    }


    /**
     * Value of an Update.
     * If both value and fileName are null, the field is set to null
     * If value is non-null, the field is updated by the value
     * If fileName is non-null, the field is updated by the file
     */
    public static class Value {

        String value;
        @Setter
        String fileName;
        @Getter
        transient ResultFile file;

        Value( String value, ResultFile file ) {
            this.value = value;
            this.file = file;
        }
    }
}
