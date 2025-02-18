package org.snd.metadata.providers.bookwalker

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.snd.metadata.MetadataProvider
import org.snd.metadata.NameSimilarityMatcher
import org.snd.metadata.model.Image
import org.snd.metadata.model.MediaType
import org.snd.metadata.model.Provider
import org.snd.metadata.model.Provider.BOOK_WALKER
import org.snd.metadata.model.SeriesSearchResult
import org.snd.metadata.model.metadata.ProviderBookId
import org.snd.metadata.model.metadata.ProviderBookMetadata
import org.snd.metadata.model.metadata.ProviderSeriesId
import org.snd.metadata.model.metadata.ProviderSeriesMetadata
import org.snd.metadata.providers.bookwalker.model.BookWalkerBook
import org.snd.metadata.providers.bookwalker.model.BookWalkerBookId
import org.snd.metadata.providers.bookwalker.model.BookWalkerCategory.LIGHT_NOVELS
import org.snd.metadata.providers.bookwalker.model.BookWalkerCategory.MANGA
import org.snd.metadata.providers.bookwalker.model.BookWalkerSearchResult
import org.snd.metadata.providers.bookwalker.model.BookWalkerSeriesBook
import org.snd.metadata.providers.bookwalker.model.BookWalkerSeriesId
import org.snd.metadata.providers.bookwalker.model.toSeriesSearchResult

class BookWalkerMetadataProvider(
    private val client: BookWalkerClient,
    private val metadataMapper: BookWalkerMapper,
    private val nameMatcher: NameSimilarityMatcher,
    private val fetchSeriesCovers: Boolean,
    private val fetchBookCovers: Boolean,
    mediaType: MediaType,
) : MetadataProvider {
    private val category = if (mediaType == MediaType.MANGA) MANGA else LIGHT_NOVELS

    override fun providerName(): Provider = BOOK_WALKER

    override fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val books = getAllBooks(BookWalkerSeriesId(seriesId.id))
        val firstBook = getFirstBook(books)
        val thumbnail = if (fetchSeriesCovers) getThumbnail(firstBook.imageUrl) else null
        return metadataMapper.toSeriesMetadata(BookWalkerSeriesId(seriesId.id), firstBook, books, thumbnail)
    }

    override fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        val bookMetadata = client.getBook(BookWalkerBookId(bookId.id))
        val thumbnail = if (fetchBookCovers) getThumbnail(bookMetadata.imageUrl) else null

        return metadataMapper.toBookMetadata(bookMetadata, thumbnail)
    }

    override fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        val searchResults = client.searchSeries(sanitizeSearchInput(seriesName.take(100)), category).take(limit)
        return searchResults.mapNotNull {
            getSeriesId(it)?.let { seriesId -> it.toSeriesSearchResult(seriesId) }
        }
    }

    override fun matchSeriesMetadata(seriesName: String): ProviderSeriesMetadata? {
        val searchResults = client.searchSeries(sanitizeSearchInput(seriesName.take(100)), category)

        return searchResults
            .firstOrNull { nameMatcher.matches(seriesName, it.seriesName) }
            ?.let {
                getSeriesId(it)?.let { seriesId ->
                    val books = getAllBooks(seriesId)
                    val firstBook = getFirstBook(books)
                    val thumbnail = if (fetchSeriesCovers) getThumbnail(firstBook.imageUrl) else null
                    metadataMapper.toSeriesMetadata(seriesId, firstBook, books, thumbnail)
                }
            }
    }

    private fun getSeriesId(searchResult: BookWalkerSearchResult): BookWalkerSeriesId? {
        return searchResult.seriesId ?: searchResult.bookId?.let { client.getBook(it).seriesId }
    }

    private fun getThumbnail(url: String?): Image? = url?.toHttpUrl()?.let { client.getThumbnail(it) }

    private fun getFirstBook(books: Collection<BookWalkerSeriesBook>): BookWalkerBook {
        val firstBook = books.sortedWith(compareBy(nullsLast()) { it.number?.start }).first()
        return client.getBook(firstBook.id)
    }

    private fun getAllBooks(series: BookWalkerSeriesId): Collection<BookWalkerSeriesBook> {
        return generateSequence(client.getSeriesBooks(series, 1)) {
            if (it.page == it.totalPages) null
            else client.getSeriesBooks(series, it.page + 1)
        }.flatMap { it.books }.toList()
    }

    private fun sanitizeSearchInput(name: String): String {
        return name
            .replace("[(]([^)]+)[)]".toRegex(), "")
            .trim()
    }
}
