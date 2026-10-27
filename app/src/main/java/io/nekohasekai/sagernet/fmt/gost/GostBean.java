/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or         *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.gost;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class GostBean extends AbstractBean {

    // gost client mode: forward protocol over ssh transport
    public static final int MODE_RELAY_SSH = 0;
    public static final int MODE_SOCKS5_SSH = 1;
    public static final int MODE_SSH = 2;
    public static final int MODE_SSHD = 3;

    public static final int AUTH_TYPE_NONE = 0;
    public static final int AUTH_TYPE_PASSWORD = 1;
    public static final int AUTH_TYPE_PRIVATE_KEY = 2;

    public String username;
    public Integer authType;
    public String password;
    public String privateKey;
    public String privateKeyPassphrase;
    public Integer mode;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == null) serverPort = 22;

        super.initializeDefaultValues();

        if (username == null) username = "root";
        if (authType == null) authType = AUTH_TYPE_PASSWORD;
        if (password == null) password = "";
        if (privateKey == null) privateKey = "";
        if (privateKeyPassphrase == null) privateKeyPassphrase = "";
        if (mode == null) mode = MODE_RELAY_SSH;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(username);
        output.writeInt(authType);
        switch (authType) {
            case AUTH_TYPE_NONE:
                break;
            case AUTH_TYPE_PASSWORD:
                output.writeString(password);
                break;
            case AUTH_TYPE_PRIVATE_KEY:
                output.writeString(privateKey);
                output.writeString(privateKeyPassphrase);
                break;
        }
        output.writeInt(mode);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        authType = input.readInt();
        switch (authType) {
            case AUTH_TYPE_NONE:
                break;
            case AUTH_TYPE_PASSWORD:
                password = input.readString();
                break;
            case AUTH_TYPE_PRIVATE_KEY:
                privateKey = input.readString();
                privateKeyPassphrase = input.readString();
                break;
        }
        if (version >= 1) {
            mode = input.readInt();
        } else {
            mode = MODE_RELAY_SSH;
        }
    }

    @NotNull
    @Override
    public GostBean clone() {
        return KryoConverters.deserialize(new GostBean(), KryoConverters.serialize(this));
    }

    public static final Creator<GostBean> CREATOR = new CREATOR<GostBean>() {
        @NonNull
        @Override
        public GostBean newInstance() {
            return new GostBean();
        }

        @Override
        public GostBean[] newArray(int size) {
            return new GostBean[size];
        }
    };
}
