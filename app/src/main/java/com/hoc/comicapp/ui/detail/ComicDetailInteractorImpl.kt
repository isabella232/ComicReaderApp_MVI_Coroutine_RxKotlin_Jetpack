package com.hoc.comicapp.ui.detail

import com.hoc.comicapp.data.ComicRepository
import com.hoc.comicapp.data.models.Comic
import com.hoc.comicapp.utils.fold
import io.reactivex.Observable
import io.reactivex.rxkotlin.cast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.rx2.rxObservable

@ExperimentalCoroutinesApi
class ComicDetailInteractorImpl(private val comicRepository: ComicRepository) :
  ComicDetailInteractor {
  companion object {
    @JvmStatic
    private fun toComicDetail(comic: Comic): ComicDetail.Comic {
      return ComicDetail.Comic(
        title = comic.title,
        link = comic.link,
        view = comic.view!!,
        status = comic.moreDetail!!.status,
        shortenedContent = comic.moreDetail.shortenedContent,
        otherName = comic.moreDetail.otherName,
        categories = comic.moreDetail.categories.map {
          Category(
            link = it.link,
            name = it.name
          )
        },
        author = comic.moreDetail.author,
        lastUpdated = comic.moreDetail.lastUpdated,
        thumbnail = comic.thumbnail,
        chapters = comic.chapters.map {
          Chapter(
            name = it.chapterName,
            link = it.chapterLink,
            time = it.time,
            view = it.view
          )
        }
      )
    }
  }

  override fun refreshPartialChanges(
    coroutineScope: CoroutineScope,
    link: String
  ): Observable<ComicDetailPartialChange> {
    return coroutineScope.rxObservable {
      send(ComicDetailPartialChange.RefreshPartialChange.Loading)

      comicRepository
        .getComicDetail(link)
        .fold(
          { ComicDetailPartialChange.RefreshPartialChange.Error(it) },
          { ComicDetailPartialChange.RefreshPartialChange.Success(toComicDetail(it)) }
        )
        .let { send(it) }
    }.cast()
  }

  override fun getComicDetail(
    coroutineScope: CoroutineScope,
    link: String,
    name: String,
    thumbnail: String
  ): Observable<ComicDetailPartialChange> {
    return coroutineScope.rxObservable<ComicDetailPartialChange> {
      send(
        ComicDetailPartialChange.InitialPartialChange.InitialData(
          initialComic = ComicDetail.InitialComic(
            title = name,
            thumbnail = thumbnail,
            link = link
          )
        )
      )
      send(ComicDetailPartialChange.InitialPartialChange.Loading)

      comicRepository
        .getComicDetail(link)
        .fold(
          { ComicDetailPartialChange.InitialPartialChange.Error(it) },
          { ComicDetailPartialChange.InitialPartialChange.Data(toComicDetail(it)) }
        )
        .let { send(it) }
    }
  }
}