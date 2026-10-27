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

package io.nekohasekai.sagernet.fmt.gost

import io.nekohasekai.sagernet.fmt.LOCALHOST
import java.net.URLEncoder

private fun urlEncode(value: String): String {
    return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

private fun GostBean.scheme(): String {
    return when (mode) {
        GostBean.MODE_SOCKS5_SSH -> "socks5+ssh"
        GostBean.MODE_SSH -> "ssh"
        GostBean.MODE_SSHD -> "sshd"
        else -> "relay+ssh"
    }
}

// gost local socks -> ssh forward (relay+ssh / socks5+ssh / ssh)
// e.g. gost -L socks5://127.0.0.1:1080 -F relay+ssh://user:pass@host:22
fun GostBean.buildGostArgs(port: Int, gostPath: String, privateKeyPath: String?): List<String> {
    val userInfo = when (authType) {
        GostBean.AUTH_TYPE_PASSWORD -> "${urlEncode(username ?: "")}:${urlEncode(password ?: "")}"
        else -> urlEncode(username ?: "")
    }

    val forward = StringBuilder(scheme())
        .append("://")
        .append(userInfo)
        .append("@")
        .append(serverAddress)
        .append(":")
        .append(serverPort)

    val params = mutableListOf<String>()
    if (authType == GostBean.AUTH_TYPE_PRIVATE_KEY && !privateKeyPath.isNullOrBlank()) {
        params.add("privateKey=$privateKeyPath")
    }
    if (params.isNotEmpty()) {
        forward.append("?").append(params.joinToString("&"))
    }

    return listOf(
        gostPath,
        "-L", "socks5://$LOCALHOST:$port",
        "-F", forward.toString(),
    )
}
