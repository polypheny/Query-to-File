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

package org.polypheny.qtf;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.qtf.web.Result;


@Slf4j
public class Console extends QueryInterface {

    public Console() {
        super();

        //on shutdown
        Runtime.getRuntime().addShutdownHook( new Thread( () -> {
            myFuse.umount();
            socketClient.close();
        } ) );

        BufferedReader br = new BufferedReader( new InputStreamReader( System.in ) );
        while ( true ) {
            try {
                System.out.println( "Enter a query or type 'help' to find more options:" );
                String query = br.readLine();
                String lowerCase = query.toLowerCase();
                if ( lowerCase.equals( "help" ) ) {
                    System.out.print( "Options:\n'commit': Commit all changes in the file system.\n"
                            + "schema.table: Enter a table identifier (e.g. public.depts) to select all rows from that table and to be able to commit changes in the file system.\n"
                            + "SELECT query: Enter any SELECT query. The result will be mapped to the file system.\n" );
                } else if ( lowerCase.equals( "commit" ) ) {
                    Result result = super.commit();
                    if ( result.error != null ) {
                        System.out.println( "The commit failed: " + result.error );
                    } else {
                        if ( result.info.affectedRows == 1 ) {
                            System.out.println( "The commit was successful. 1 row was affected." );
                        } else {
                            System.out.printf( "The commit was successful. %d rows were affected.\n", result.info.affectedRows );
                        }
                    }
                } else if ( lowerCase.startsWith( "select" ) || lowerCase.startsWith( "delete" ) || lowerCase.startsWith( "update" ) ) {
                    super.submitQueryRequest( query );
                } else {
                    super.submitTableRequest( query );
                }
            } catch ( IOException e ) {
                log.error( "Could not read line", e );
            }
        }
    }

    @Override
    public void onResultUpdate( Result result ) {
        if ( result.error != null ) {
            System.out.println( "The query failed" );
        } else if ( result.data == null && result.info != null ) {
            System.out.printf( "The query was successful and affected %d rows\n", result.info.affectedRows );
        } else {
            System.out.printf( "Fetched %d rows\n", result.data.length );
        }
    }
}
