/*
 * Created by Andrii Kovalchuk
 * Copyright (C) 2021. roove
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses
 */

package com.mmdev.business.user

import com.mmdev.business.pairs.MatchedUserItem
import com.mmdev.business.photo.PhotoItem
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface IUserRepository {

	fun deleteMatchedUser(user: UserItem, matchedUserItem: MatchedUserItem): Single<Unit>

	fun deletePhoto(userItem: UserItem, photoItem: PhotoItem, isMainPhotoDeleting: Boolean): Completable

	fun deleteMyself(user: UserItem): Completable

	fun getRequestedUserItem(baseUserInfo: BaseUserInfo): Single<UserItem>

	fun submitReport(report: Report): Completable

	fun updateUserItem(userItem: UserItem): Completable

	fun uploadUserProfilePhoto(
		userItem: UserItem,
		photoUri: String
	): Observable<HashMap<Double, List<PhotoItem>>>

}