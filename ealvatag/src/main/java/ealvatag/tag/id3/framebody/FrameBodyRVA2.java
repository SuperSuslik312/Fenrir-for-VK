/**
 * @author : Paul Taylor
 * @author : Eric Farng
 * <p>
 * Version @version:$Id$
 * <p>
 * MusicTag Copyright (C)2003,2004
 * <p>
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public  License as
 * published by the Free Software Foundation; either version 2.1 of the License, or (at your option) any later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, you can get a copy from
 * http://www.opensource.org/licenses/lgpl-license.php or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 * <p>
 * Description: Relative Volume Adjustment
 */
package ealvatag.tag.id3.framebody;

import java.nio.ByteBuffer;

import ealvatag.tag.InvalidTagException;
import ealvatag.tag.datatype.ByteArraySizeTerminated;
import ealvatag.tag.datatype.DataTypes;
import ealvatag.tag.id3.ID3v24Frames;
import okio.Buffer;

public class FrameBodyRVA2 extends AbstractID3v2FrameBody implements ID3v24FrameBody {

    /**
     * Creates a new FrameBodyRVA2 datatype.
     */
    public FrameBodyRVA2() {
    }

    public FrameBodyRVA2(FrameBodyRVA2 body) {
        super(body);
    }

    /**
     * Convert from V3 to V4 Frame
     *
     * @param body
     */
    public FrameBodyRVA2(FrameBodyRVAD body) {
        setObjectValue(DataTypes.OBJ_DATA, body.getObjectValue(DataTypes.OBJ_DATA));
    }


    /**
     * Creates a new FrameBodyRVAD datatype.
     *
     * @param byteBuffer
     * @param frameSize
     * @throws InvalidTagException if unable to create framebody from buffer
     */
    public FrameBodyRVA2(ByteBuffer byteBuffer, int frameSize) throws InvalidTagException {
        super(byteBuffer, frameSize);
    }

    public FrameBodyRVA2(Buffer byteBuffer, int frameSize) throws InvalidTagException {
        super(byteBuffer, frameSize);
    }

    /**
     * The ID3v2 frame identifier
     *
     * @return the ID3v2 frame identifier  for this frame type
     */
    public String getIdentifier() {
        return ID3v24Frames.FRAME_ID_RELATIVE_VOLUME_ADJUSTMENT2;
    }

    /**
     * Setup the Object List. A byte Array which will be read upto frame size
     * bytes.
     */
    protected void setupObjectList() {
        addDataType(new ByteArraySizeTerminated(DataTypes.OBJ_DATA, this));
    }


}
