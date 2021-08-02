package ealvatag.tag.datatype;

import static ealvatag.logging.EalvaTagLog.LogLevel.ERROR;
import static ealvatag.logging.EalvaTagLog.LogLevel.TRACE;
import static ealvatag.logging.EalvaTagLog.LogLevel.WARN;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ealvatag.tag.InvalidDataTypeException;
import ealvatag.tag.TagOptionSingleton;
import ealvatag.tag.exceptions.IllegalCharsetException;
import ealvatag.tag.id3.AbstractTagFrameBody;
import ealvatag.utils.StandardCharsets;
import okio.Buffer;

/**
 * Represents a String which is not delimited by null character.
 * <p>
 * This type of String will usually only be used when it is the last field within a frame, when reading the remainder of
 * the byte array will be read, when writing the frame will be accommodate the required size for the String. The String
 * will be encoded based upon the text encoding of the frame that it belongs to.
 * <p>
 * All TextInformation frames support multiple strings, stored as a null separated list, where null is represented by
 * the termination code for the character encoding. This functionality is only officially support in ID3v24.
 * <p>
 * Most applications will ignore any but the first value, but some such as Foobar2000 will decode them properly
 * <p>
 * iTunes write null terminators characters after the String even though it only writes a single value.
 */
public class TextEncodedStringSizeTerminated extends AbstractString {

    /**
     * Creates a new empty TextEncodedStringSizeTerminated datatype.
     *
     * @param identifier identifies the frame type
     */
    public TextEncodedStringSizeTerminated(String identifier, AbstractTagFrameBody frameBody) {
        super(identifier, frameBody);
    }

    /**
     * Copy constructor
     */
    public TextEncodedStringSizeTerminated(TextEncodedStringSizeTerminated object) {
        super(object);
    }

    /**
     * Split the values separated by null character
     *
     * @param value the raw value
     * @return list of values, guaranteed to be at least one value
     */
    public static List<String> splitByNullSeperator(String value) {
        String[] valuesarray = value.split("\\u0000");
        List<String> values = Arrays.asList(valuesarray);
        //Read only list so if empty have to create new list
        if (values.size() == 0) {
            values = new ArrayList<String>(1);
            values.add("");
        }
        return values;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof TextEncodedStringSizeTerminated && super.equals(obj);
    }

    /**
     * Read a 'n' bytes from buffer into a String where n is the framesize - offset
     * so therefore cannot use this if there are other objects after it because it has no
     * delimiter.
     * <p>
     * Must take into account the text encoding defined in the Encoding Object
     * ID3 Text Frames often allow multiple strings seperated by the null char
     * appropriate for the encoding.
     *
     * @param arr    this is the buffer for the frame
     * @param offset this is where to start reading in the buffer for this field
     * @throws NullPointerException      if arr is null
     * @throws IndexOutOfBoundsException if offset is not within arr bounds
     */
    public void readByteArray(byte[] arr, int offset) throws InvalidDataTypeException {
        LOG.log(TRACE, "Reading from array from offset:%s", offset);


        //Decode sliced inBuffer
        ByteBuffer inBuffer;
        if (TagOptionSingleton.getInstance().isAndroid()) {
            //#302 [dallen] truncating array manually since the decoder.decode() does not honor the offset in the in
            // buffer
            byte[] truncArr = new byte[arr.length - offset];
            System.arraycopy(arr, offset, truncArr, 0, truncArr.length);
            inBuffer = ByteBuffer.wrap(truncArr);
        } else {
            inBuffer = ByteBuffer.wrap(arr, offset, arr.length - offset).slice();
        }

        CharBuffer outBuffer = CharBuffer.allocate(arr.length - offset);


        CharsetDecoder decoder = getCorrectDecoder(inBuffer);
        CoderResult coderResult = decoder.decode(inBuffer, outBuffer, true);
        if (coderResult.isError()) {
            LOG.log(WARN, "Decoding error:%s", coderResult);
        }
        decoder.flush(outBuffer);
        outBuffer.flip();

        //If using UTF16 with BOM we then search through the text removing any BOMs that could exist
        //for multiple values, BOM could be Big Endian or Little Endian
        if (StandardCharsets.UTF_16.equals(getTextEncodingCharSet())) {
            value = outBuffer.toString().replace("\ufeff", "").replace("\ufffe", "");
        } else {
            value = outBuffer.toString();
        }
        //SetSize, important this is correct for finding the next datatype
        setSize(arr.length - offset);
        LOG.log(TRACE, "Read SizeTerminatedString:%s size:%s", value, size);

    }

    @Override
    public void read(Buffer buffer, int size) throws EOFException, InvalidDataTypeException {
        try {
            long bufferStartSize = buffer.size();
            Charset decoder = peekCorrectDecoder(buffer);
            String outBuffer = buffer.readString(size, decoder);

            //If using UTF16 with BOM we then search through the text removing any BOMs that could exist
            //for multiple values, BOM could be Big Endian or Little Endian
            if (StandardCharsets.UTF_16.equals(getTextEncodingCharSet())) {
                value = outBuffer.replace("\ufeff", "").replace("\ufffe", "");
            } else {
                value = outBuffer;
            }
            setSize((int) (bufferStartSize - buffer.size()));
        } catch (IllegalCharsetException e) {
            throw new InvalidDataTypeException(e, "Bad charset id");
        }
    }

    /**
     * Write String using specified encoding
     * <p>
     * When this is called multiple times, all but the last value has a trailing null
     */
    @SuppressWarnings("WeakerAccess")
    protected ByteBuffer writeString(CharsetEncoder encoder, String next, int i, int noOfValues)
            throws CharacterCodingException {

        ByteBuffer bb;
        if ((i + 1) == noOfValues) {
            bb = encoder.encode(CharBuffer.wrap(next));
        } else {
            bb = encoder.encode(CharBuffer.wrap(next + '\0'));
        }
        bb.rewind();
        return bb;
    }

    /**
     * Write String in UTF-LEBOM format
     * <p>
     * When this is called multiple times, all but the last value has a trailing null
     * <p>
     * Remember we are using this charset because the charset that writes BOM does it the wrong way for us
     * so we use this none and then manually add the BOM ourselves.
     *
     * @throws CharacterCodingException on encoding error
     */
    @SuppressWarnings("WeakerAccess")
    protected ByteBuffer writeStringUTF16LEBOM(String next, int i, int noOfValues) throws CharacterCodingException {
        CharsetEncoder encoder = StandardCharsets.UTF_16LE.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);

        ByteBuffer bb;
        //Note remember LE BOM is ff fe but this is handled by encoder Unicode char is fe ff
        if ((i + 1) == noOfValues) {
            bb = encoder.encode(CharBuffer.wrap('\ufeff' + next));
        } else {
            bb = encoder.encode(CharBuffer.wrap('\ufeff' + next + '\0'));
        }
        bb.rewind();
        return bb;
    }

    /**
     * Write String in UTF-BEBOM format
     * <p>
     * When this is called multiple times, all but the last value has a trailing null
     *
     * @throws CharacterCodingException on encoding error
     */
    @SuppressWarnings("WeakerAccess")
    protected ByteBuffer writeStringUTF16BEBOM(String next, int i, int noOfValues)
            throws CharacterCodingException {
        CharsetEncoder encoder = StandardCharsets.UTF_16BE.newEncoder();
        encoder.onMalformedInput(CodingErrorAction.IGNORE);
        encoder.onUnmappableCharacter(CodingErrorAction.IGNORE);

        ByteBuffer bb;
        //Add BOM
        if ((i + 1) == noOfValues) {
            bb = encoder.encode(CharBuffer.wrap('\ufeff' + next));
        } else {
            bb = encoder.encode(CharBuffer.wrap('\ufeff' + next + '\0'));
        }
        bb.rewind();
        return bb;
    }

    /**
     * Removing trailing null from end of String, this should not be there but some applications continue to write
     * this unnecessary null char.
     */
    protected void stripTrailingNull() {
        if (TagOptionSingleton.getInstance().isRemoveTrailingTerminatorOnWrite()) {
            String stringValue = (String) value;
            if (stringValue.length() > 0) {
                if (stringValue.charAt(stringValue.length() - 1) == '\0') {
                    stringValue = (stringValue).substring(0, stringValue.length() - 1);
                    value = stringValue;
                }
            }
        }
    }

    /**
     * Because nulls are stripped we need to check if not removing trailing nulls whether the original
     * value ended with a null and if so add it back in.
     *
     * @param values
     * @param stringValue
     */
    protected void checkTrailingNull(List<String> values, String stringValue) {
        if (!TagOptionSingleton.getInstance().isRemoveTrailingTerminatorOnWrite()) {
            if (stringValue.length() > 0 && stringValue.charAt(stringValue.length() - 1) == '\0') {
                String lastVal = values.get(values.size() - 1);
                String newLastVal = lastVal + '\0';
                values.set(values.size() - 1, newLastVal);
            }
        }
    }

    /**
     * Write String into byte array
     * <p>
     * It will remove a trailing null terminator if exists if the option
     * RemoveTrailingTerminatorOnWrite has been set.
     *
     * @return the data as a byte array in format to write to file
     */
    public byte[] writeByteArray() {
        byte[] data;
        //Try and write to buffer using the CharSet defined by getTextEncodingCharSet()
        Charset charset = getTextEncodingCharSet();
        try {

            stripTrailingNull();

            //Special Handling because there is no UTF16 BOM LE charset
            String stringValue = (String) value;
            Charset actualCharSet = null;
            if (StandardCharsets.UTF_16.equals(charset)) {
                if (TagOptionSingleton.getInstance().isEncodeUTF16BomAsLittleEndian()) {
                    actualCharSet = StandardCharsets.UTF_16LE;
                } else {
                    actualCharSet = StandardCharsets.UTF_16BE;
                }
            }

            //Ensure large enough for any encoding
            ByteBuffer outputBuffer = ByteBuffer.allocate((stringValue.length() + 3) * 3);

            //Ensure each string (if multiple values) is written with BOM by writing separately
            List<String> values = splitByNullSeperator(stringValue);
            checkTrailingNull(values, stringValue);

            //For each value
            for (int i = 0; i < values.size(); i++) {
                String next = values.get(i);

                if (StandardCharsets.UTF_16LE.equals(actualCharSet)) {
                    outputBuffer.put(writeStringUTF16LEBOM(next, i, values.size()));
                } else if (StandardCharsets.UTF_16BE.equals(actualCharSet)) {
                    outputBuffer.put(writeStringUTF16BEBOM(next, i, values.size()));
                } else {
                    CharsetEncoder charsetEncoder = charset.newEncoder();
                    charsetEncoder.onMalformedInput(CodingErrorAction.IGNORE);
                    charsetEncoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
                    outputBuffer.put(writeString(charsetEncoder, next, i, values.size()));
                }
            }
            outputBuffer.flip();
            data = new byte[outputBuffer.limit()];
            outputBuffer.rewind();
            outputBuffer.get(data, 0, outputBuffer.limit());
            setSize(data.length);
        }
        //https://bitbucket.org/ijabz/jaudiotagger/issue/1/encoding-metadata-to-utf-16-can-fail-if
        catch (CharacterCodingException ce) {
            LOG.log(ERROR, "Character coding charset:%s value:%s", charset, value, ce);
            throw new RuntimeException(ce);
        }
        return data;
    }

    /**
     * Add an additional String to the current String value
     *
     * @param value
     */
    public void addValue(String value) {
        setValue(this.value + "\u0000" + value);
    }

    /**
     * How many values are held, each value is separated by a null terminator
     *
     * @return number of values held, usually this will be one.
     */
    public int getNumberOfValues() {
        return splitByNullSeperator(((String) value)).size();
    }

    /**
     * Get the nth value
     *
     * @param index
     * @return the nth value
     * @throws IndexOutOfBoundsException if value does not exist
     */
    public String getValueAtIndex(int index) {
        //Split String into separate components
        List values = splitByNullSeperator((String) value);
        return (String) values.get(index);
    }

    /**
     * @return list of all values
     */
    public List<String> getValues() {
        return splitByNullSeperator((String) value);
    }

    /**
     * Get value(s) whilst removing any trailing nulls
     */
    public String getValueWithoutTrailingNull() {
        if (value != null) {
            List<String> values = splitByNullSeperator((String) value);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i != 0) {
                    sb.append("\u0000");
                }
                sb.append(values.get(i));
            }
            return sb.toString();
        } else {
            LOG.log(ERROR, "value is null");
            return "";
        }
    }
}