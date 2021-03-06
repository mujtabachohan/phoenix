/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may 
 *     be used to endorse or promote products derived from this software without 
 *     specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.salesforce.phoenix.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.Format;
import java.util.Calendar;
import java.util.Map;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;

import com.salesforce.phoenix.compile.ColumnProjector;
import com.salesforce.phoenix.compile.RowProjector;
import com.salesforce.phoenix.exception.SQLExceptionCode;
import com.salesforce.phoenix.exception.SQLExceptionInfo;
import com.salesforce.phoenix.iterate.ResultIterator;
import com.salesforce.phoenix.query.Scanner;
import com.salesforce.phoenix.schema.PDataType;
import com.salesforce.phoenix.schema.tuple.ResultTuple;
import com.salesforce.phoenix.schema.tuple.Tuple;
import com.salesforce.phoenix.util.SQLCloseable;



/**
 * 
 * JDBC ResultSet implementation of Phoenix.
 * Currently only the following data types are supported:
 * - String
 * - Date
 * - Time
 * - Timestamp
 * - BigDecimal
 * - Double
 * - Float
 * - Int
 * - Short
 * - Long
 * - Binary
 * None of the update or delete methods are supported.
 * The ResultSet only supports the following options:
 * - ResultSet.FETCH_FORWARD
 * - ResultSet.CONCUR_READ_ONLY
 * - ResultSet.TYPE_FORWARD_ONLY
 * - ResultSet.CLOSE_CURSORS_AT_COMMIT
 * 
 * @author jtaylor
 * @since 0.1
 */
public class PhoenixResultSet implements ResultSet, SQLCloseable, com.salesforce.phoenix.jdbc.Jdbc7Shim.ResultSet {
    private final static String STRING_FALSE = "0";
    private final static BigDecimal BIG_DECIMAL_FALSE = BigDecimal.valueOf(0);
    private final static Integer INTEGER_FALSE = Integer.valueOf(0);
    private final static Tuple BEFORE_FIRST = new ResultTuple();
    
    private final ResultIterator scanner;
    private final RowProjector rowProjector;
    private final PhoenixStatement statement;
    private final ImmutableBytesWritable ptr = new ImmutableBytesWritable();
    
    private Tuple currentRow = BEFORE_FIRST;
    private boolean isClosed = false;
    private boolean wasNull = false;
    
    public PhoenixResultSet(ResultIterator resultIterator, RowProjector rowProjector, PhoenixStatement statement) throws SQLException {
        this.rowProjector = rowProjector;
        this.scanner = resultIterator;
        this.statement = statement;
    }
    
    public PhoenixResultSet(Scanner scanner, PhoenixStatement statement) throws SQLException {
        this.rowProjector = scanner.getProjection();
        this.scanner = scanner.iterator();
        this.statement = statement;
    }
    
    @Override
    public boolean absolute(int row) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void afterLast() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void beforeFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void clearWarnings() throws SQLException {
    }

    @Override
    public void close() throws SQLException {
        if (isClosed) {
            return;
        }
        try {
            scanner.close();
        } finally {
            isClosed = true;
            statement.getResultSets().remove(this);
        }
    }

    @Override
    public void deleteRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        Integer index = rowProjector.getColumnIndex(columnLabel);
        return index + 1;
    }

    @Override
    public boolean first() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Array getArray(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    private void checkOpen() throws SQLException {
        if (isClosed) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.RESULTSET_CLOSED).build().buildException();
        }
    }

    private void checkCursorState() throws SQLException {
        checkOpen();
        if (currentRow == BEFORE_FIRST) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.CURSOR_BEFORE_FIRST_ROW).build().buildException();
        }else if (currentRow == null) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.CURSOR_PAST_LAST_ROW).build().buildException();
        }
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkCursorState();
        BigDecimal value = (BigDecimal)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.DECIMAL, ptr);
        wasNull = (value == null);
        return value;
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        BigDecimal value = getBigDecimal(columnIndex);
        return value.setScale(scale);
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkCursorState();
        ColumnProjector colProjector = rowProjector.getColumnProjector(columnIndex-1);
        PDataType type = colProjector.getExpression().getDataType();
        Object value = colProjector.getValue(currentRow, type, ptr);
        wasNull = (value == null);
        if (value == null) {
            return false;
        }
        switch(type) {
        case BOOLEAN:
            return Boolean.TRUE.equals(value);
        case VARCHAR:
            return !STRING_FALSE.equals(value);
        case INTEGER:
            return !INTEGER_FALSE.equals(value);
        case DECIMAL:
            return !BIG_DECIMAL_FALSE.equals(value);
        default:
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.CANNOT_CALL_METHOD_ON_TYPE)
                .setMessage("Method: getBoolean; Type:" + type).build().buildException();
        }
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        checkCursorState();
        byte[] value = (byte[])rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.VARBINARY, ptr);
        wasNull = (value == null);
        return value;
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
//        throw new SQLFeatureNotSupportedException();
        checkCursorState();
        Byte value = (Byte)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.TINYINT, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        checkCursorState();
        Date value = (Date)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.DATE, ptr);
        wasNull = (value == null);
        if (value == null) {
            return null;
        }
        return value;
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        checkCursorState();
        Date value = (Date)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.DATE, ptr);
        cal.setTime(value);
        return new Date(cal.getTimeInMillis());
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return getDate(findColumn(columnLabel), cal);
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkCursorState();
        Double value = (Double)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.DOUBLE, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public int getFetchSize() throws SQLException {
        return statement.getFetchSize();
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        checkCursorState();
        Float value = (Float)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.FLOAT, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkCursorState();
        Integer value = (Integer)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.INTEGER, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkCursorState();
        Long value = (Long)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.LONG, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return new PhoenixResultSetMetaData(statement.getConnection(), rowProjector);
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        checkCursorState();
        ColumnProjector projector = rowProjector.getColumnProjector(columnIndex-1);
        Object value = projector.getValue(currentRow, projector.getExpression().getDataType(), ptr);
        wasNull = (value == null);
        return value;
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex); // Just ignore map since we only support built-in types
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(findColumn(columnLabel), map);
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public int getRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        // TODO: support?
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        checkCursorState();
        Short value = (Short)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.SMALLINT, ptr);
        wasNull = (value == null);
        if (value == null) {
            return 0;
        }
        return value;
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public Statement getStatement() throws SQLException {
        return statement;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        checkCursorState();
        // Get the value using the expected type instead of trying to coerce to VARCHAR.
        // We can't coerce using our formatter because we don't have enough context in PDataType.
        ColumnProjector projector = rowProjector.getColumnProjector(columnIndex-1);
        PDataType type = projector.getExpression().getDataType();
        Object value = projector.getValue(currentRow,type, ptr);
        if (wasNull = (value == null)) {
            return null;
        }
        // Run Object through formatter to get String.
        // This provides a simple way of getting a reasonable string representation
        // for types like DATE and TIME
        Format formatter = statement.getFormatter(type);
        return formatter == null ? value.toString() : formatter.format(value);
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        checkCursorState();
        Time value = (Time)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.TIME, ptr);
        wasNull = (value == null);
        return value;
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkCursorState();
        Time value = (Time)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.TIME, ptr);
        wasNull = (value == null);
        if (value == null) {
            return null;
        }
        cal.setTime(value);
        value.setTime(cal.getTimeInMillis());
        return value;
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return getTime(findColumn(columnLabel),cal);
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkCursorState();
        Timestamp value = (Timestamp)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.TIMESTAMP, ptr);
        wasNull = (value == null);
        return value;
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        checkCursorState();
        Timestamp value = (Timestamp)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.TIMESTAMP, ptr);
        wasNull = (value == null);
        if (value == null) {
            return null;
        }
        cal.setTime(value);
        value.setTime(cal.getTimeInMillis()); // TODO: are nanos preserved?
        return value;
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return getTimestamp(findColumn(columnLabel),cal);
    }

    @Override
    public int getType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        checkCursorState();
        String value = (String)rowProjector.getColumnProjector(columnIndex-1).getValue(currentRow, PDataType.VARCHAR, ptr);
        wasNull = (value == null);
        if (value == null) {
            return null;
        }
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.MALFORMED_URL).setRootCause(e).build().buildException();
        }
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        return getURL(findColumn(columnLabel));
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void insertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return currentRow == null;
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return currentRow == BEFORE_FIRST;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return isClosed;
    }

    @Override
    public boolean isFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean isLast() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean last() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean next() throws SQLException {
        checkOpen();
        currentRow = scanner.next();
        return currentRow != null;
    }

    @Override
    public boolean previous() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean rowInserted() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        if (direction != ResultSet.FETCH_FORWARD) {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRow() throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public boolean wasNull() throws SQLException {
        return wasNull;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!iface.isInstance(this)) {
            throw new SQLExceptionInfo.Builder(SQLExceptionCode.CLASS_NOT_UNWRAPPABLE)
                .setMessage(this.getClass().getName() + " not unwrappable from " + iface.getName())
                .build().buildException();
        }
        return (T)this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        return (T) getObject(columnIndex); // Just ignore type since we only support built-in types
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return (T) getObject(columnLabel); // Just ignore type since we only support built-in types
    }
}
