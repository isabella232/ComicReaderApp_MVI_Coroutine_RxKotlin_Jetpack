package com.hoc.comicapp.data.firebase.favorite_comics

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.hoc.comicapp.data.firebase.entity._FavoriteComic
import com.hoc.comicapp.data.firebase.user.FirebaseAuthUserDataSource
import com.hoc.comicapp.domain.models.AuthError
import com.hoc.comicapp.domain.thread.CoroutinesDispatcherProvider
import com.hoc.comicapp.domain.thread.RxSchedulerProvider
import com.hoc.comicapp.utils.Either
import com.hoc.comicapp.utils.fold
import com.hoc.comicapp.utils.left
import com.hoc.comicapp.utils.right
import com.hoc.comicapp.utils.snapshots
import io.reactivex.Observable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FavoriteComicsDataSourceImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firebaseFirestore: FirebaseFirestore,
  private val rxSchedulerProvider: RxSchedulerProvider,
  private val dispatcherProvider: CoroutinesDispatcherProvider,
  private val firebaseAuthUserDataSource: FirebaseAuthUserDataSource
) : FavoriteComicsDataSource {
  override fun isFavorited(url: String): Observable<Either<Throwable, Boolean>> {
    return firebaseAuthUserDataSource
      .userObservable()
      .switchMap { either ->
        either.fold(
          { Observable.just(it.left()) },
          {
            val collection = favoriteCollectionForCurrentUserOrNull
              ?: return@fold Observable.just(AuthError.Unauthenticated.left())

            collection
              .whereEqualTo("url", url)
              .limit(1)
              .snapshots()
              .map { it.documents.isNotEmpty().right() as Either<Throwable, Boolean> }
              .onErrorReturn { it.left() }
              .subscribeOn(rxSchedulerProvider.io)
          }
        )
      }
  }

  override fun favoriteComics(): Observable<Either<Throwable, List<_FavoriteComic>>> {
    return firebaseAuthUserDataSource
      .userObservable()
      .switchMap { either ->
        either.fold(
          { Observable.just(it.left()) },
          {
            val collection = favoriteCollectionForCurrentUserOrNull
              ?: return@fold Observable.just(AuthError.Unauthenticated.left())

            collection
              .orderBy("created_at", Query.Direction.DESCENDING)
              .snapshots()
              .map { querySnapshot ->
                querySnapshot
                  .documents
                  .mapNotNull { it.toObject(_FavoriteComic::class.java) }
                  .distinctBy { it.url }
                  .right() as Either<Throwable, List<_FavoriteComic>>
              }
              .onErrorReturn { it.left() }
              .subscribeOn(rxSchedulerProvider.io)
          }
        )
      }
  }

  override suspend fun removeFromFavorite(comic: _FavoriteComic) {
    withContext(dispatcherProvider.io) {
      val snapshot = findQueryDocumentSnapshotByUrl(comic.url)
      if (snapshot?.exists() == true) {
        snapshot.reference.delete().await()
      } else {
        error("Comic is not exists")
      }
    }
  }

  override suspend fun toggle(comic: _FavoriteComic) {
    withContext(dispatcherProvider.io) {
      val snapshot = findQueryDocumentSnapshotByUrl(comic.url)
      if (snapshot?.exists() == true) {
        snapshot.reference.delete().await()
        Timber.d("Remove from favorites: $comic")
      } else {
        (favoriteCollectionForCurrentUserOrNull
          ?: throw AuthError.Unauthenticated).add(comic).await()
        Timber.d("Insert to favorites: $comic")
      }
    }
  }

  private val favoriteCollectionForCurrentUserOrNull: CollectionReference?
    get() = firebaseAuth.currentUser?.uid?.let {
      firebaseFirestore.collection("users/${it}/favorite_comics")
    }

  /**
   * @return [QueryDocumentSnapshot] or null
   * @throws AuthError.Unauthenticated if not logged in
   */
  @Throws(AuthError.Unauthenticated::class)
  private suspend fun findQueryDocumentSnapshotByUrl(url: String): QueryDocumentSnapshot? {
    return (favoriteCollectionForCurrentUserOrNull ?: throw AuthError.Unauthenticated)
      .whereEqualTo("url", url)
      .limit(1)
      .get()
      .await()
      .firstOrNull()
  }
}