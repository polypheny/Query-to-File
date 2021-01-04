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


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class QTFConfig {

    static Properties prop;


    static {
        prop = new Properties();
        //see https://stackoverflow.com/questions/16343939/loading-properties-file-from-static-context
        try ( InputStream input = QTFConfig.class.getClassLoader().getResourceAsStream( "config.properties" ) ) {
            prop.load( input );
        } catch ( IOException ex ) {
            log.error( "Could not load properties" );
            System.exit( 1 );
        }
    }


    public static String getFileUrl( String fileName ) {
        return String.format( "http://%s:%s/getFile/%s", prop.getProperty( "host" ), prop.getProperty( "port" ), fileName );
    }

    public static String getRestInterface( String method ) {
        return String.format( "http://%s:%s/%s", prop.getProperty( "host" ), prop.getProperty( "port" ), method );
    }

    public static String getWebSocketUrl() {
        return String.format( "ws://%s:%s/webSocket", prop.getProperty( "host" ), prop.getProperty( "port" ) );
    }

    public static String getLibraryPath() {
        return prop.getProperty( "libraryPath" );
    }

    public static String getLibfuse() {
        return getLibraryPath() + prop.getProperty( "libfuse" );
    }

    public static int getFuseCapacityGB() {
        return Integer.parseInt( prop.getProperty( "fuseCapacityGB" ) );
    }

    public static int getReconnectionTimeout() {
        return Integer.parseInt( prop.getProperty( "reconnectionTimeout" ) );
    }

}
