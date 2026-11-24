package com.lanweixi.feature.docs

import com.lanweixi.core.data.entity.DocChunkEntity
import com.lanweixi.core.data.entity.DocumentEntity
import com.lanweixi.core.data.repository.DocumentRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P135：文档库 ViewModel 单测（纯 JVM，Fake 仓库）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDocs : DocumentRepository {
        val docs = mutableListOf<DocumentEntity>()
        val chunks = mutableListOf<DocChunkEntity>()
        var nextId = 1L
        var nextChunkId = 1L

        override suspend fun listAll() = docs.sortedByDescending { it.importedAt }
        override suspend fun byId(id: Long) = docs.firstOrNull { it.id == id }
        override suspend fun chunksOf(docId: Long) = chunks.filter { it.docId == docId }.sortedBy { it.seq }
        override suspend fun search(query: String) = docs.filter { doc ->
            doc.title.contains(query) || chunks.any { it.docId == doc.id && it.text.contains(query) }
        }
        override suspend fun firstHit(docId: Long, query: String) =
            chunks.firstOrNull { it.docId == docId && it.text.contains(query) }
        override suspend fun import(title: String, fileName: String, kind: String, chunks: List<String>): Long {
            val id = nextId++
            docs += DocumentEntity(
                id = id, title = title, fileName = fileName, kind = kind,
                charCount = chunks.sumOf { it.length }, chunkCount = chunks.size,
                importedAt = id * 100L, updatedAt = id * 100L,
            )
            chunks.forEachIndexed { index, text ->
                this.chunks += DocChunkEntity(
                    id = nextChunkId++, docId = id, seq = index, text = text, charCount = text.length,
                )
            }
            return id
        }
        override suspend fun deleteById(id: Long) {
            docs.removeAll { it.id == id }
            chunks.removeAll { it.docId == id }
        }
        override suspend fun count() = docs.size
    }

    private fun vm(repo: FakeDocs) = DocsViewModel(repo)

    @Test
    fun `init loads docs`() = runTest {
        val repo = FakeDocs().apply {
            docs += DocumentEntity(
                id = 1, title = "A", fileName = "a.txt", kind = "txt",
                charCount = 3, chunkCount = 1, importedAt = 1, updatedAt = 1,
            )
        }
        val vm = vm(repo)
        assertEquals(listOf("A"), vm.list.value.docs.map { it.title })
    }

    @Test
    fun `import txt creates doc and chunks and opens detail`() = runTest {
        val repo = FakeDocs()
        val vm = vm(repo)
        vm.import("note.txt") { ByteArrayInputStream("第一段\n\n第二段".toByteArray()) }

        assertEquals(1, repo.docs.size)
        assertEquals("note", repo.docs[0].title)
        assertEquals("txt", repo.docs[0].kind)
        assertEquals(2, repo.docs[0].chunkCount)
        assertEquals(listOf("第一段", "第二段"), repo.chunks.map { it.text })

        // 导入后详情应已载入
        assertEquals("note", vm.detail.value.doc?.title)
        assertEquals(2, vm.detail.value.chunks.size)
        assertNotNull(vm.list.value.notice)
    }

    @Test
    fun `import docx extracts paragraphs`() = runTest {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                (
                    "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                        "<w:body><w:p><w:r><w:t>标题行</w:t></w:r></w:p><w:p><w:r><w:t>正文</w:t></w:r></w:p></w:body></w:document>"
                    ).toByteArray(),
            )
            zip.closeEntry()
        }
        val repo = FakeDocs()
        val vm = vm(repo)
        vm.import("方案.docx") { ByteArrayInputStream(out.toByteArray()) }

        assertEquals("docx", repo.docs[0].kind)
        assertEquals("标题行\n正文", repo.chunks.joinToString("\n") { it.text })
    }

    @Test
    fun `unsupported format surfaces an error and keeps library empty`() = runTest {
        val repo = FakeDocs()
        val vm = vm(repo)
        vm.import("a.pdf") { ByteArrayInputStream(ByteArray(0)) }

        assertTrue(repo.docs.isEmpty())
        assertNotNull(vm.list.value.error)
        assertTrue(vm.list.value.error!!.contains("txt"))
    }

    @Test
    fun `search hits chunk text`() = runTest {
        val repo = FakeDocs()
        val vm = vm(repo)
        vm.import("a.txt") { ByteArrayInputStream("番茄炒蛋的做法".toByteArray()) }
        vm.import("b.txt") { ByteArrayInputStream("Kotlin 协程".toByteArray()) }

        vm.onQueryChange("番茄")
        assertEquals(listOf("a"), vm.list.value.docs.map { it.title })
        vm.onQueryChange("不存在的词")
        assertTrue(vm.list.value.docs.isEmpty())
        vm.onQueryChange("")
        assertEquals(2, vm.list.value.docs.size)
    }

    @Test
    fun `delete removes doc and its chunks`() = runTest {
        val repo = FakeDocs()
        val vm = vm(repo)
        vm.import("x.txt") { ByteArrayInputStream("内容".toByteArray()) }
        val id = repo.docs[0].id

        vm.delete(id) { }
        assertTrue(repo.docs.isEmpty())
        assertTrue(repo.chunks.isEmpty())
        assertNull(repo.byId(id))
    }
}