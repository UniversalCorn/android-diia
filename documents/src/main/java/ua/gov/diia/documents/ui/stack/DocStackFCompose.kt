package ua.gov.diia.documents.ui.stack

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import ua.gov.diia.core.models.ConsumableString
import ua.gov.diia.core.ui.dynamicdialog.ActionsConst
import ua.gov.diia.core.util.delegation.WithBuildConfig
import ua.gov.diia.core.util.delegation.WithCrashlytics
import ua.gov.diia.core.util.extensions.activity.setWindowBrightness
import ua.gov.diia.core.util.extensions.fragment.currentDestinationId
import ua.gov.diia.core.util.extensions.fragment.handlePhoneIntent
import ua.gov.diia.core.util.extensions.fragment.navigateOnce
import ua.gov.diia.core.util.extensions.fragment.registerForNavigationResult
import ua.gov.diia.core.util.extensions.fragment.sendImage
import ua.gov.diia.core.util.extensions.fragment.sendPdf
import ua.gov.diia.diia_storage.AndroidBase64Wrapper
import ua.gov.diia.documents.NavDocActionsDirections
import ua.gov.diia.documents.R
import ua.gov.diia.documents.helper.DocumentsHelper
import ua.gov.diia.documents.models.DiiaDocument
import ua.gov.diia.documents.ui.gallery.DocGalleryNavigationHelper
import ua.gov.diia.documents.ui.gallery.DocGalleryVMCompose
import ua.gov.diia.documents.ui.stack.compose.StackScreen
import ua.gov.diia.documents.util.view.showCopyDocIdClipedSnackBar
import ua.gov.diia.ui_base.components.infrastructure.collectAsEffect
import ua.gov.diia.ui_base.components.infrastructure.event.UIActionKeysCompose
import ua.gov.diia.ui_base.navigation.BaseNavigation
import ua.gov.diia.ui_base.util.navigation.openTemplateDialog
import ua.gov.diia.web.util.extensions.fragment.navigateToWebView
import javax.inject.Inject

@AndroidEntryPoint
class DocStackFCompose : Fragment() {

    @Inject
    lateinit var withCrashlytics: WithCrashlytics

    @Inject
    lateinit var withBuildConfig: WithBuildConfig

    @Inject
    lateinit var navigationHelper: DocGalleryNavigationHelper

    @Inject
    lateinit var documentsHelper: DocumentsHelper
    private var composeView: ComposeView? = null
    private val viewModel: DocStackVMCompose by viewModels()
    private val args: DocStackFComposeArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.configureTopBar(getHeader())
        viewModel.subscribeForDocuments(args.docType)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        composeView = ComposeView(requireContext())
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.invalidateDataSource()
        composeView?.setContent {
            val topBar = viewModel.topBarData

            val body = viewModel.bodyData
            val progressIndicator =
                viewModel.progressIndicator.collectAsState(
                    initial = Pair(
                        "",
                        true
                    )
                )
            val contentLoaded = viewModel.contentLoaded.collectAsState(
                initial = Pair(
                    UIActionKeysCompose.PAGE_LOADING_TRIDENT, true
                )
            )

            viewModel.apply {
                navigation.collectAsEffect { navigation ->
                    when (navigation) {
                        is BaseNavigation.Back -> {
                            findNavController().popBackStack()
                        }

                        is DocStackVMCompose.Navigation.ToDocActions -> {
                            navigateToDocActions(
                                navigation.doc,
                                navigation.position
                            )
                        }

                        is DocStackVMCompose.Navigation.NavToVehicleInsurance -> {
                            documentsHelper.onTickerClick(this@DocStackFCompose, navigation.doc)
                        }
                    }
                }
                docAction.collectAsEffect { action ->
                    when (action) {
                        is DocStackVMCompose.DocActions.OpenDriverAccount -> {
                            navigateToWebView(getString(R.string.url_driver_e_find_address))
                        }

                        is DocStackVMCompose.DocActions.OpenElectronicQueue -> {
                            navigateToWebView(getString(R.string.url_driver_e_queue))
                        }

                        is DocStackVMCompose.DocActions.Call -> {
                            handlePhoneIntent(getString(R.string.phone_number_veteran_registry))
                        }

                        is DocStackVMCompose.DocActions.ShareImage -> {
                            sendImage(
                                action.data.byteArray,
                                action.data.fileName,
                                withBuildConfig.getApplicationId()
                            )
                        }

                        is DocStackVMCompose.DocActions.HighBrightness -> {
                            activity?.setWindowBrightness()
                        }

                        is DocStackVMCompose.DocActions.DefaultBrightness -> {
                            activity?.setWindowBrightness(true)
                        }

                        is DocStackVMCompose.DocActions.DocNumberCopy -> {
                            composeView?.showCopyDocIdClipedSnackBar(
                                action.value, 40f
                            )
                        }
                    }
                }
            }

            viewModel.certificatePdf.observe(viewLifecycleOwner) { event ->
                event.getContentIfNotHandled()?.let { e ->
                    val bArray = AndroidBase64Wrapper().decode(e.docPDF.toByteArray())
                    sendPdf(bArray, event.peekContent().name, withBuildConfig.getApplicationId())
                }
            }

            viewModel.documentPdf.observe(viewLifecycleOwner) { event ->
                event.getContentIfNotHandled()?.let { e ->
                    val bArray = AndroidBase64Wrapper().decode(e.docPDF.toByteArray())
                    sendPdf(
                        bArray,
                        event.peekContent().name,
                        withBuildConfig.getApplicationId()
                    )
                }
            }

            registerForNavigationResult<ConsumableString>(ActionsConst.FRAGMENT_USER_ACTION_RESULT_KEY) {
                viewModel.checkStackCount()
            }

            viewModel.showTemplateDialog.collectAsEffect {
                viewModel.stopLoading()
                openTemplateDialog(it.peekContent())
            }
            viewModel.showRatingDialogByUserInitiative.collectAsEffect {
                viewModel.stopLoading()
                val ratingModel = it.peekContent()
                documentsHelper.navigateToRatingService(
                    this,
                    viewModel,
                    ratingModel,
                    isFromStack = true
                )
            }

            StackScreen(
                contentLoaded = contentLoaded.value,
                progressIndicator = progressIndicator.value,
                toolbar = topBar,
                body = body,
                onEvent = {
                    viewModel.onUIAction(it)
                })
        }
        navigationHelper.subscribeForStackNavigationEvents(this, viewModel)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView = null
    }

    private fun navigateToDocActions(doc: DiiaDocument, position: Int) {

        navigateOnce(
            NavDocActionsDirections.actionGlobalDestinationDocActionsCompose(
                doc = doc,
                position = position,
                enableStackActions = true,
                currentlyDisplayedOdcTypes = args.docType,
                manualDocs = null,
                resultDestinationId = currentDestinationId ?: return
            ),
            targetDestinationId = R.id.destination_docActionsCompose
        )
    }

    private fun getHeader(): String {
        return documentsHelper.getStackHeader(this, args.docType)
    }

    override fun onPause() {
        super.onPause()
        activity?.setWindowBrightness(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.scrollToLastDocPos()
    }
}