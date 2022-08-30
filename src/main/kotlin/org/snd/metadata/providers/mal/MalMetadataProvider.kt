package org.snd.metadata.providers.mal

import org.snd.metadata.MetadataProvider
import org.snd.metadata.NameSimilarityMatcher
import org.snd.metadata.model.Provider
import org.snd.metadata.model.Provider.MAL
import org.snd.metadata.model.ProviderBookId
import org.snd.metadata.model.ProviderBookMetadata
import org.snd.metadata.model.ProviderSeriesId
import org.snd.metadata.model.ProviderSeriesMetadata
import org.snd.metadata.model.SeriesSearchResult
import org.snd.metadata.providers.mal.model.toSeriesSearchResult


class MalMetadataProvider(
    private val malClient: MalClient,
    private val metadataMapper: MalMetadataMapper,
    private val nameMatcher: NameSimilarityMatcher,
) : MetadataProvider {

    override fun providerName(): Provider {
        return MAL
    }

    override fun getSeriesMetadata(seriesId: ProviderSeriesId): ProviderSeriesMetadata {
        val series = malClient.getSeries(seriesId.id.toInt())
        val thumbnail = malClient.getThumbnail(series)

        return metadataMapper.toSeriesMetadata(series, thumbnail)
    }

    override fun getBookMetadata(seriesId: ProviderSeriesId, bookId: ProviderBookId): ProviderBookMetadata {
        throw UnsupportedOperationException()
    }

    override fun searchSeries(seriesName: String, limit: Int): Collection<SeriesSearchResult> {
        return malClient.searchSeries(seriesName.take(64)).results.take(limit)
            .map { it.toSeriesSearchResult() }
    }

    override fun matchSeriesMetadata(seriesName: String): ProviderSeriesMetadata? {
        val searchResults = malClient.searchSeries(seriesName.take(64))
        val match = searchResults.results.firstOrNull {
            val titles = listOfNotNull(it.title, it.alternative_titles.ja, it.alternative_titles.ja) + it.alternative_titles.synonyms
            nameMatcher.matches(seriesName, titles)
        }

        return match?.let {
            val series = malClient.getSeries(it.id)
            val thumbnail = malClient.getThumbnail(series)
            metadataMapper.toSeriesMetadata(series, thumbnail)
        }
    }
}
