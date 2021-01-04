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

package com.mmdev.roove.ui.chat.view

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mmdev.domain.chat.MessageItem
import com.mmdev.domain.conversations.ConversationItem
import com.mmdev.domain.pairs.MatchedUserItem
import com.mmdev.domain.user.data.BaseUserInfo
import com.mmdev.domain.user.data.ReportType.DISRESPECTFUL_BEHAVIOR
import com.mmdev.domain.user.data.ReportType.FAKE
import com.mmdev.domain.user.data.ReportType.INELIGIBLE_PHOTOS
import com.mmdev.domain.user.data.UserItem
import com.mmdev.roove.BuildConfig
import com.mmdev.roove.R
import com.mmdev.roove.core.permissions.AppPermission
import com.mmdev.roove.core.permissions.handlePermission
import com.mmdev.roove.core.permissions.onRequestPermissionsResultReceived
import com.mmdev.roove.core.permissions.requestAppPermissions
import com.mmdev.roove.databinding.FragmentChatBinding
import com.mmdev.roove.ui.MainActivity
import com.mmdev.roove.ui.chat.ChatViewModel
import com.mmdev.roove.ui.common.base.BaseFragment
import com.mmdev.roove.ui.profile.RemoteRepoViewModel
import com.mmdev.roove.utils.extensions.hideKeyboard
import com.mmdev.roove.utils.extensions.observeOnce
import com.mmdev.roove.utils.extensions.showToastText
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.*


/**
 * This is the documentation block about the class
 */

@AndroidEntryPoint
class ChatFragment : BaseFragment<ChatViewModel, FragmentChatBinding>(
	layoutId = R.layout.fragment_chat
) {
	
	override val mViewModel: ChatViewModel by viewModels()
	private val remoteRepoViewModel: RemoteRepoViewModel by viewModels()

	private var receivedPartnerCity = ""
	private var receivedPartnerGender = ""
	private var receivedPartnerId = ""
	private var receivedConversationId = ""
	
	private var isReported: Boolean = false

	private lateinit var currentConversation: ConversationItem
	private lateinit var currentPartner: UserItem

	private val mChatAdapter: ChatAdapter = ChatAdapter().apply {
		//set current user id to understand left/right message
		setCurrentUserId(MainActivity.currentUser!!.baseUserInfo.userId)
	}

	// File
	private lateinit var mFilePathImageCamera: File

	

	//static fields
	companion object {

		private const val IMAGE_GALLERY_REQUEST = 1
		private const val IMAGE_CAMERA_REQUEST = 2

		private const val PARTNER_CITY_KEY = "PARTNER_CITY"
		private const val PARTNER_GENDER_KEY = "PARTNER_GENDER"
		private const val PARTNER_ID_KEY = "PARTNER_ID"
		private const val CONVERSATION_ID_KEY = "CONVERSATION_ID"

	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		//deep link from notification
		arguments?.let { handleDeepLink(it) }
		
		//setup
		remoteRepoViewModel.retrievedUserItem.observeOnce(this, {
			currentPartner = it
			mViewModel.partnerName.value = it.baseUserInfo.name.split(" ")[0]
			mViewModel.partnerPhoto.value = it.baseUserInfo.mainPhotoUrl
		})
		
		mViewModel.newMessage.observe(this, {
			mChatAdapter.newMessage(it)
		})

		remoteRepoViewModel.reportSubmittingStatus.observeOnce(this, {
			isReported = it
			requireContext().showToastText(getString(R.string.toast_text_report_success))
		})
		
		sharedViewModel.conversationSelected.observeOnce(this, {
			currentConversation = it
			remoteRepoViewModel.getRequestedUserInfo(it.partner)
			mViewModel.observeNewMessages(it)
			//mViewModel.observePartnerOnline(it.conversationId)
			mViewModel.loadMessages(it, 0)
		})

	}
	
	private fun handleDeepLink(bundle: Bundle) {
		receivedPartnerCity = bundle.getString(PARTNER_CITY_KEY, "")
		receivedPartnerGender = bundle.getString(PARTNER_GENDER_KEY, "")
		receivedPartnerId = bundle.getString(PARTNER_ID_KEY, "")
		receivedConversationId = bundle.getString(CONVERSATION_ID_KEY, "")
		if (receivedPartnerCity.isNotEmpty() &&
			receivedPartnerGender.isNotEmpty() &&
			receivedPartnerId.isNotEmpty() &&
			receivedConversationId.isNotEmpty()
		) {
			//if it was a deep link navigation then create ConversationItem "on a flight"
			
			sharedViewModel.matchedUserItemSelected.value =
				MatchedUserItem(
					baseUserInfo = BaseUserInfo(userId = receivedPartnerId),
					conversationId = receivedConversationId,
					conversationStarted = true
				)
			sharedViewModel.conversationSelected.value =
				ConversationItem(
					partner = BaseUserInfo(userId = receivedPartnerId),
					conversationId = receivedConversationId,
					conversationStarted = true
				)
		}
		
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) = binding.run {

		root.setOnTouchListener { v, _ ->
			v.performClick()
			v.hideKeyboard(edTextMessageInput)
		}
		
		edTextMessageInput.doOnTextChanged { text, start, before, count ->
			btnSendMessage.isActivated = text?.trim().isNullOrBlank()
		}

		btnSendMessage.setOnClickListener { sendMessageClick() }

		//show attachment dialog picker
		btnSendAttachment.setOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setItems(
					arrayOf(
						getString(R.string.material_dialog_picker_camera),
						getString(R.string.material_dialog_picker_gallery)
					)
				) { _, itemIndex ->
					when (itemIndex) {
						0 -> photoCameraClick()
						1 -> photoGalleryClick()
					}
				}
				.create()
				.apply { window?.attributes?.gravity = Gravity.BOTTOM }
				.show()
		}

		//if message contains photo then it opens in fullscreen dialog
		mChatAdapter.setOnAttachedPhotoClickListener { view, position, photoItem ->
			photoItem?.fileUrl?.let {
				FullScreenDialogFragment
					.newInstance(it)
					.show(childFragmentManager, FullScreenDialogFragment::class.java.canonicalName)
			}
		}
		
		
		rvMessageList.apply {
			adapter = mChatAdapter
			layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
			
			//todo load more messages on scroll
		}

		toolbarChat.setNavigationOnClickListener { navController.navigateUp() }

		toolbarChat.setOnMenuItemClickListener { item ->
			when (item.itemId) {
				R.id.chat_action_report -> { if (!isReported) showReportDialog() }
			}
			return@setOnMenuItemClickListener true
		}

		toolbarInnerContainer.setOnClickListener {
			navController.navigate(R.id.action_chat_to_profileFragment)
		}
	}

	/**
	 * Send plain text msg to chat if editText is not empty
	 * else shake animation
	 */
	private fun sendMessageClick() = binding.run {
		if (edTextMessageInput.text.toString().trim().isNotEmpty()) {

			val message = MessageItem(
				sender = MainActivity.currentUser!!.baseUserInfo,
				recipientId = currentConversation.partner.userId,
				text = edTextMessageInput.text.toString().trim(),
				photoItem = null,
				conversationId = currentConversation.conversationId
			)

			mViewModel.sendMessage(message)
			mChatAdapter.newMessage(message)
			rvMessageList.scrollToPosition(0)
			edTextMessageInput.text?.clear()
		}
		else edTextMessageInput.startAnimation(AnimationUtils.loadAnimation(context, R.anim.horizontal_shake))

	}

	/**
	 * Checks if the app has permissions to OPEN CAMERA and take photos
	 * If the app does not has permission then the user will be prompted to grant permissions
	 * else open camera intent
	 */
	private fun photoCameraClick() = handlePermission(
		AppPermission.CAMERA,
		onGranted = { startCameraIntent() },
		onDenied = { requestAppPermissions(it) },
		onExplanationNeeded = {
			/** Additional explanation for permission usage needed **/
		}
	)

	//take photo directly by camera
	private fun startCameraIntent() {
		val namePhoto = DateFormat.format("yyyy-MM-dd_hhmmss", Date()).toString()
		mFilePathImageCamera = File(
			requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), namePhoto + "camera.jpg")
		val photoURI = FileProvider.getUriForFile(
			requireContext(),
			BuildConfig.APPLICATION_ID + ".provider",
			mFilePathImageCamera
		)
		val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
			putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
		}
		startActivityForResult(intent, IMAGE_CAMERA_REQUEST)
	}

	/**
	 * Checks if the app has permissions to READ user files
	 * If the app does not has permission then the user will be prompted to grant permissions
	 * else open gallery to choose photo
	 */
	private fun photoGalleryClick() = handlePermission(
		AppPermission.GALLERY,
		onGranted = { startGalleryIntent() },
		onDenied = { requestAppPermissions(it) },
		onExplanationNeeded = {
			/** Additional explanation for permission usage needed **/
		}
	)
	

	//open gallery chooser
	private fun startGalleryIntent() {
		val intent = Intent().apply {
			action = Intent.ACTION_GET_CONTENT
			type = "image/*"
		}
		startActivityForResult(Intent.createChooser(intent, "Select image"), IMAGE_GALLERY_REQUEST)
	}

	// start after permissions was granted
	// If request is cancelled, the result arrays are empty.
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		onRequestPermissionsResultReceived(
				requestCode,
				grantResults,
				onPermissionGranted = {
					when (it) {
						AppPermission.CAMERA -> startCameraIntent()
						AppPermission.GALLERY -> startGalleryIntent()
					}
				},
				onPermissionDenied = {
					requireContext().showToastText(getString(it.deniedMessageId))
				}
		)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		// send photo from gallery
		if (requestCode == IMAGE_GALLERY_REQUEST) {
			if (resultCode == RESULT_OK) {

				val selectedUri = data?.data
				mViewModel.sendPhoto(
					photoUri = selectedUri.toString(),
					conversation = currentConversation,
					sender = MainActivity.currentUser!!.baseUserInfo
				)
			}
		}
		// send photo taken by camera
		if (requestCode == IMAGE_CAMERA_REQUEST) {
			if (resultCode == RESULT_OK) {

				if (mFilePathImageCamera.exists()) {
					mViewModel.sendPhoto(
						photoUri = Uri.fromFile(mFilePathImageCamera).toString(),
						conversation = currentConversation,
						sender = MainActivity.currentUser!!.baseUserInfo
					)
				}
				else requireContext().showToastText(
					"filePathImageCamera is null or filePathImageCamera isn't exists"
				)
			}
		}
	}

	private fun showReportDialog() = MaterialAlertDialogBuilder(requireContext())
		.setItems(
			arrayOf(
				getString(R.string.report_chooser_photos),
				getString(R.string.report_chooser_behavior),
				getString(R.string.report_chooser_fake)
			)
		) { _, itemIndex ->
			when (itemIndex) {
				0 -> remoteRepoViewModel.submitReport(INELIGIBLE_PHOTOS, currentPartner.baseUserInfo)
				1 -> remoteRepoViewModel.submitReport(DISRESPECTFUL_BEHAVIOR, currentPartner.baseUserInfo)
				2 -> remoteRepoViewModel.submitReport(FAKE, currentPartner.baseUserInfo)
				
			}
		}
		.create()
		.apply { window?.attributes?.gravity = Gravity.CENTER }
		.show()
	

	override fun onBackPressed() {
		navController.navigateUp()
	}

}
