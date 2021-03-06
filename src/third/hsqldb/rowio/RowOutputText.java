/* Copyright (c) 2001-2016, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package third.hsqldb.rowio;


import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;

import third.hsqldb.Row;
import third.hsqldb.error.Error;
import third.hsqldb.error.ErrorCode;
import third.hsqldb.lib.StringConverter;
import third.hsqldb.persist.TextFileSettings;
import third.hsqldb.types.BinaryData;
import third.hsqldb.types.BlobData;
import third.hsqldb.types.ClobData;
import third.hsqldb.types.IntervalMonthData;
import third.hsqldb.types.IntervalSecondData;
import third.hsqldb.types.JavaObjectData;
import third.hsqldb.types.TimeData;
import third.hsqldb.types.TimestampData;
import third.hsqldb.types.Type;
import third.hsqldb.types.Types;


/**
 *  Class for writing the data for a database row in text table format.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @version 2.3.4
 * @since 1.7.0
 */
public class RowOutputText extends RowOutputBase {

    protected String           fieldSep;
    protected String           varSep;
    protected String           longvarSep;
    private boolean            fieldSepEnd;
    private boolean            varSepEnd;
    private boolean            longvarSepEnd;
    private String             nextSep = "";
    private boolean            nextSepEnd;
    protected TextFileSettings textFileSettings;

    public RowOutputText(TextFileSettings textFileSettings) {

        super();

        initTextDatabaseRowOutput(textFileSettings);
    }

    private void initTextDatabaseRowOutput(TextFileSettings textFileSettings) {

        this.textFileSettings = textFileSettings;
        this.fieldSep         = textFileSettings.fs;
        this.varSep           = textFileSettings.vs;
        this.longvarSep       = textFileSettings.lvs;

        //-- Newline indicates that field should match to end of line.
        if (fieldSep.endsWith("\n")) {
            fieldSepEnd = true;
            fieldSep    = fieldSep.substring(0, fieldSep.length() - 1);
        }

        if (varSep.endsWith("\n")) {
            varSepEnd = true;
            varSep    = varSep.substring(0, varSep.length() - 1);
        }

        if (longvarSep.endsWith("\n")) {
            longvarSepEnd = true;
            longvarSep    = longvarSep.substring(0, longvarSep.length() - 1);
        }
    }

    public void setStorageSize(int size) {}

    public void writeEnd() {

        // terminate at the end of row
        if (nextSepEnd) {
            writeBytes(nextSep);
        }

        writeBytes(TextFileSettings.NL);
    }

    public void writeSize(int size) {

        // initialise at the start of row
        nextSep    = "";
        nextSepEnd = false;
    }

    public void writeType(int type) {

        //--do Nothing
    }

    public void writeString(String s) {

        s = checkConvertString(s, fieldSep);

        // error
        if (s == null) {
            return;
        }

        byte[] bytes = getBytes(s);

        write(bytes, 0, bytes.length);

        nextSep    = fieldSep;
        nextSepEnd = fieldSepEnd;
    }

    protected void writeVarString(String s) {

        s = checkConvertString(s, varSep);

        if (s == null) {
            return;
        }

        byte[] bytes = getBytes(s);

        write(bytes, 0, bytes.length);

        nextSep    = varSep;
        nextSepEnd = varSepEnd;
    }

    protected void writeLongVarString(String s) {

        s = checkConvertString(s, longvarSep);

        if (s == null) {
            return;
        }

        byte[] bytes = getBytes(s);

        write(bytes, 0, bytes.length);

        nextSep    = longvarSep;
        nextSepEnd = longvarSepEnd;
    }

    protected String checkConvertString(String s, String sep) {

        if (s.indexOf('\n') != -1 || s.indexOf('\r') != -1) {
            throw new IllegalArgumentException(
                Error.getMessage(ErrorCode.TEXT_STRING_HAS_NEWLINE));
        } else if (s.indexOf(sep) != -1) {
            return null;
        }

        return s;
    }

    private byte[] getBytes(String s) {

        byte[] bytes = null;

        try {
            bytes = s.getBytes(textFileSettings.charEncoding);
        } catch (UnsupportedEncodingException e) {
            throw Error.error(ErrorCode.TEXT_FILE_IO, e);
        }

        return bytes;
    }

    protected void writeByteArray(byte[] b) {

        if (textFileSettings.isUTF16) {
            byte[] buffer = new byte[b.length * 2];

            StringConverter.writeHexBytes(buffer, 0, b);

            String s = new String(buffer);

            writeBytes(s);
        } else {
            ensureRoom(b.length * 2);
            StringConverter.writeHexBytes(this.getBuffer(), count, b);

            count += b.length * 2;
        }
    }

    public void writeShort(int i) {
        writeInt(i);
    }

    public void writeInt(int i) {

        writeBytes(Integer.toString(i));

        nextSep    = fieldSep;
        nextSepEnd = fieldSepEnd;
    }

    public void writeLong(long i) {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowOutputText");
    }

    public void writeBytes(String s) {

        if (textFileSettings.isUTF16) {
            try {
                if (s.length() > 0) {
                    byte[] bytes = s.getBytes(textFileSettings.charEncoding);

                    super.write(bytes);
                }
            } catch (UnsupportedEncodingException e) {
                throw Error.error(ErrorCode.TEXT_FILE_IO, e);
            }
        } else {
            super.writeBytes(s);
        }
    }

// fredt@users - comment - methods used for writing each SQL type
    protected void writeFieldType(Type type) {

        writeBytes(nextSep);

        switch (type.typeCode) {

            case Types.SQL_VARCHAR :
                nextSep    = varSep;
                nextSepEnd = varSepEnd;
                break;

            default :
                nextSep    = fieldSep;
                nextSepEnd = fieldSepEnd;
                break;
        }
    }

    protected void writeNull(Type type) {
        writeFieldType(type);
    }

    protected void writeChar(String s, Type t) {

        switch (t.typeCode) {

            case Types.SQL_CHAR :
                writeString(s);

                return;

            case Types.SQL_VARCHAR :
                writeVarString(s);

                return;

            default :
                writeLongVarString(s);

                return;
        }
    }

    protected void writeSmallint(Number o) {
        writeString(o.toString());
    }

    protected void writeInteger(Number o) {
        writeString(o.toString());
    }

    protected void writeBigint(Number o) {
        writeString(o.toString());
    }

    protected void writeReal(Double o) {
        writeString(o.toString());
    }

    protected void writeDecimal(BigDecimal o, Type type) {
        writeString(type.convertToString(o));
    }

    protected void writeBoolean(Boolean o) {
        writeString(o.toString());
    }

    protected void writeDate(TimestampData o, Type type) {
        writeString(type.convertToString(o));
    }

    protected void writeTime(TimeData o, Type type) {
        writeString(type.convertToString(o));
    }

    protected void writeTimestamp(TimestampData o, Type type) {
        writeString(type.convertToString(o));
    }

    protected void writeYearMonthInterval(IntervalMonthData o, Type type) {
        this.writeBytes(type.convertToString(o));
    }

    protected void writeDaySecondInterval(IntervalSecondData o, Type type) {
        this.writeBytes(type.convertToString(o));
    }

    protected void writeOther(JavaObjectData o) {

        byte[] ba = o.getBytes();

        writeByteArray(ba);
    }

    protected void writeBit(BinaryData o) {

        String s = StringConverter.byteArrayToBitString(o.getBytes(),
            (int) o.bitLength(null));

        writeString(s);
    }

    protected void writeBinary(BinaryData o) {
        writeByteArray(o.getBytes());
    }

    protected void writeClob(ClobData o, Type type) {
        writeString(Long.toString(o.getId()));
    }

    protected void writeBlob(BlobData o, Type type) {
        writeString(Long.toString(o.getId()));
    }

    protected void writeArray(Object[] o, Type type) {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowOutputText");
    }

    public int getSize(Row row) {

        reset();

        try {
            writeSize(0);
            writeData(row, row.getTable().getColumnTypes());
            writeEnd();
        } catch (Exception e) {
            reset();

//            throw Error.error(ErrorCode.FILE_IO_ERROR, e.toString());
        }

        int rowsize = size();

        reset();

        return rowsize;
    }

    public int getStorageSize(int size) {
        return size;
    }

    public RowOutputInterface duplicate() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowOutputText");
    }
}
