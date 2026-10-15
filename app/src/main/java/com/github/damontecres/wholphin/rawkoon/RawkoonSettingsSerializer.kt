package com.github.damontecres.wholphin.rawkoon

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class RawkoonSettingsSerializer
    @Inject
    constructor() : Serializer<RawkoonSettings> {
        override val defaultValue: RawkoonSettings = RawkoonSettings.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): RawkoonSettings =
            try {
                RawkoonSettings.parseFrom(input)
            } catch (e: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read Rawkoon settings", e)
            }

        override suspend fun writeTo(
            t: RawkoonSettings,
            output: OutputStream,
        ) = t.writeTo(output)
    }
