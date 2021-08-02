package ealvatag.audio.aiff.chunk;

import java.io.IOException;
import java.nio.ByteBuffer;

import ealvatag.audio.aiff.AiffAudioHeader;
import ealvatag.audio.iff.ChunkHeader;

/**
 * <p>
 * The Copyright Chunk contains a copyright notice for the sound. text contains a date followed
 * by the copyright owner. The chunk ID '(c) ' serves as the copyright characters '©'. For example,
 * a Copyright Chunk containing the text "1988 Apple Computer, Inc." means "© 1988 Apple Computer, Inc."
 * </p>
 * <p>
 * The Copyright Chunk is optional. No more than one Copyright Chunk may exist within a FORM AIFF.
 * </p>
 */
public class CopyrightChunk extends TextChunk {

    /**
     * @param chunkHeader     The header for this chunk
     * @param chunkData       The buffer from which the AIFF data are being read
     * @param aiffAudioHeader The AiffAudioHeader into which information is stored
     */
    public CopyrightChunk(ChunkHeader chunkHeader, ByteBuffer chunkData, AiffAudioHeader aiffAudioHeader) {
        super(chunkHeader, chunkData, aiffAudioHeader);
    }

    @Override
    public boolean readChunk() throws IOException {
        aiffAudioHeader.setCopyright(readChunkText());
        return true;
    }

}
