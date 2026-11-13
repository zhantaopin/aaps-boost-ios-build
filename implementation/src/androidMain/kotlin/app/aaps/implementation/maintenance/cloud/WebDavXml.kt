package app.aaps.implementation.maintenance.cloud

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One answer from a WebDAV PROPFIND request.
 */
internal data class WebDavEntry(
    val href: String,
    val isCollection: Boolean,
    val contentLength: Long?
)

/**
 * The small part of WebDAV XML that AAPS needs.
 *
 * WebDAV answers are XML, but the fields are simple: a link, whether it is a folder, and its size.
 * This parser reads only those fields. It turns off DTDs and external entities so a server cannot
 * make the app read local files or expand a very large entity tree.
 */
internal object WebDavXml {

    val PROPFIND_BODY: String = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:resourcetype/>
            <d:getcontentlength/>
          </d:prop>
        </d:propfind>
    """.trimIndent()

    fun parseMultiStatus(xml: String): List<WebDavEntry> {
        if (xml.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val document: Document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = document.getElementsByTagNameNS("*", "response")
        return buildList {
            for (index in 0 until responses.length) {
                val response = responses.item(index) as? Element ?: continue
                val href = response.firstText("href") ?: continue
                add(
                    WebDavEntry(
                        href = href,
                        isCollection = response.getElementsByTagNameNS("*", "collection").length > 0,
                        contentLength = response.firstText("getcontentlength")?.toLongOrNull()
                    )
                )
            }
        }
    }

    private fun Element.firstText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return (nodes.item(0) as? Element)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }
}
