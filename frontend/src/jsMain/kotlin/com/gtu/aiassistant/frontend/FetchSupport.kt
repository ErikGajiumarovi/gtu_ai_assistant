package com.gtu.aiassistant.frontend

import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

internal fun browserRequestInit(
    method: String,
    body: Any? = null,
    headers: Headers? = null
): RequestInit {
    val init = js("({})")
    init.method = method
    if (body != null) init.body = body
    if (headers != null) init.headers = headers
    return init.unsafeCast<RequestInit>()
}
