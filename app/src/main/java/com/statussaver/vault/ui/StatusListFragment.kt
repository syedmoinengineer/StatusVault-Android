package com.statussaver.vault.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.statussaver.vault.MainActivity
import com.statussaver.vault.R
import com.statussaver.vault.adapter.StatusAdapter
import com.statussaver.vault.databinding.FragmentStatusListBinding
import com.statussaver.vault.model.StatusItem
import com.statussaver.vault.repository.StatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StatusListFragment : Fragment() {

    companion object {
        private const val ARG_TYPE    = "type"
        private const val TYPE_IMAGES = "images"
        private const val TYPE_VIDEOS = "videos"

        fun newImages() = StatusListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, TYPE_IMAGES) }
        }

        fun newVideos() = StatusListFragment().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, TYPE_VIDEOS) }
        }
    }

    private var _b: FragmentStatusListBinding? = null
    private val b get() = _b!!

    private val isImages get() = arguments?.getString(ARG_TYPE) == TYPE_IMAGES

    private val viewModel by lazy { (requireActivity() as MainActivity).viewModel }

    private lateinit var adapter: StatusAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentStatusListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        adapter = StatusAdapter(
            onItemClick  = ::openPreview,
            onSaveClick  = ::saveItem,
            onShareClick = ::shareItem
        )
        b.recyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            this.adapter  = this@StatusListFragment.adapter
            setHasFixedSize(true)
            addItemDecoration(GridSpacingItemDecoration(spanCount = 2, spacingDp = 8))
        }
    }

    private fun setupSwipeRefresh() {
        b.swipeRefreshLayout.setOnRefreshListener {
            val uri = StatusRepository(requireContext()).getPersistedUri()
            if (uri != null) viewModel.loadStatuses(uri)
            else b.swipeRefreshLayout.isRefreshing = false
        }
        b.swipeRefreshLayout.setColorSchemeResources(R.color.md_theme_primary)
    }

    private fun observeViewModel() {
        val source = if (isImages) viewModel.images else viewModel.videos

        source.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            b.swipeRefreshLayout.isRefreshing = false
            toggleEmptyState(items.isEmpty())
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            b.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.saveEvent.observe(viewLifecycleOwner) { event ->
            event ?: return@observe
            val msg = if (event.success)
                getString(
                    R.string.saved_to,
                    if (event.isVideo) "Movies/StatusVault" else "Pictures/StatusVault"
                )
            else getString(R.string.save_failed)
            Snackbar.make(b.root, msg, Snackbar.LENGTH_SHORT).show()
            viewModel.consumeSaveEvent()
        }
    }

    private fun toggleEmptyState(empty: Boolean) {
        b.layoutEmpty.visibility  = if (empty) View.VISIBLE else View.GONE
        b.recyclerView.visibility = if (empty) View.GONE    else View.VISIBLE
        b.tvEmptyTitle.text    = if (isImages) getString(R.string.no_images_yet)
        else          getString(R.string.no_videos_yet)
        b.tvEmptySubtitle.text = getString(R.string.empty_subtitle)
    }

    private fun openPreview(item: StatusItem) {
        startActivity(
            Intent(requireContext(), PreviewActivity::class.java).apply {
                putExtra(PreviewActivity.EXTRA_URI,      item.uri.toString())
                putExtra(PreviewActivity.EXTRA_IS_VIDEO, item.isVideo)
                putExtra(PreviewActivity.EXTRA_NAME,     item.name)
                putExtra(PreviewActivity.EXTRA_IS_SAVED, item.isSaved)
            }
        )
    }

    private fun saveItem(item: StatusItem) {
        viewModel.saveStatus(item)
    }

    private fun shareItem(item: StatusItem) {
        lifecycleScope.launch {
            val shareUri = withContext(Dispatchers.IO) { buildShareUri(item) }
            if (shareUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
            } else {
                Snackbar.make(b.root, getString(R.string.share_failed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildShareUri(item: StatusItem): android.net.Uri? {
        return try {
            val ctx   = requireContext()
            val cache = File(ctx.cacheDir, "shared").apply { mkdirs() }
            val tmp   = File(cache, item.name)
            ctx.contentResolver.openInputStream(item.uri)?.use { inp ->
                tmp.outputStream().use { out -> inp.copyTo(out) }
            }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", tmp)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

// ─── Grid spacing decoration ──────────────────────────────────────────────────

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingDp: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val density = view.context.resources.displayMetrics.density
        val px: Int = (spacingDp * density).toInt()

        val position: Int = parent.getChildAdapterPosition(view)
        val column: Int   = position % spanCount

        outRect.left   = px - (column * px / spanCount)
        outRect.right  = (column + 1) * px / spanCount
        outRect.top    = if (position < spanCount) px else 0
        outRect.bottom = px
    }
}