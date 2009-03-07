package org.codehaus.mojo.unix.dpkg;

/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import fj.data.Option;
import static fj.data.Option.some;
import org.apache.commons.compress.tar.TarEntry;
import org.apache.commons.compress.tar.TarInputStream;
import org.codehaus.mojo.unix.FileAttributes;
import org.codehaus.mojo.unix.UnixFileMode;
import org.codehaus.mojo.unix.UnixFsObject;
import static org.codehaus.mojo.unix.UnixFsObject.directory;
import static org.codehaus.mojo.unix.UnixFsObject.regularFile;
import org.codehaus.mojo.unix.ar.Ar;
import org.codehaus.mojo.unix.ar.ArUtil;
import org.codehaus.mojo.unix.ar.CloseableIterable;
import org.codehaus.mojo.unix.ar.ReadableArFile;
import org.codehaus.mojo.unix.util.RelativePath;
import org.joda.time.LocalDateTime;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * @author <a href="mailto:trygvis@codehaus.org">Trygve Laugst&oslash;l</a>
 * @version $Id$
 */
public class DpkgDebTool
{
    public static List<UnixFsObject> contents( File file )
        throws IOException
    {
        CloseableIterable archive = null;
        try
        {
            archive = Ar.read( file );

            Iterator iterator = archive.iterator();

            while ( iterator.hasNext() )
            {
                ReadableArFile arFile = (ReadableArFile) iterator.next();
                if ( arFile.getName().startsWith( "data." ) )
                {
                    return processCompressedTar( arFile );
                }
            }

            throw new IOException( "Could not find data file in: " + file.getAbsolutePath() );
        }
        finally
        {
            ArUtil.close( archive );
        }
    }

    private static List<UnixFsObject> processCompressedTar( ReadableArFile arFile )
        throws IOException
    {
        if ( arFile.getName().endsWith( ".tar.gz" ) )
        {
            // Don't worry about closing the stream, that will be taken care of by the iterator.

            return process( new GZIPInputStream( arFile.open(), 1024 * 128 ) );
        }
        else
        {
            throw new IOException( "Unsupported compression format of data tar file: " + arFile.getName() );
        }
    }

    private static List<UnixFsObject> process( InputStream is )
        throws IOException
    {
        TarInputStream tarInputStream = new TarInputStream( is );

        TarEntry entry = tarInputStream.getNextEntry();

        List<UnixFsObject> objects = new ArrayList<UnixFsObject>();

        while ( entry != null )
        {
            Option<UnixFileMode> mode = some( UnixFileMode.fromInt( entry.getMode() ) );
            FileAttributes attributes = new FileAttributes( some( entry.getUserName() ), some( entry.getGroupName() ), mode );
            RelativePath path = RelativePath.fromString( entry.getName() );
            LocalDateTime lastModified = LocalDateTime.fromDateFields( entry.getModTime() );

            objects.add( entry.isDirectory() ?
                directory( path, lastModified, attributes ) :
                regularFile( path, lastModified, entry.getSize(), some( attributes ) ));

            entry = tarInputStream.getNextEntry();
        }

        return objects;
    }
}
