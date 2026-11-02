/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.AggregateRow
import io.nekohasekai.sagernet.database.ConnEntity
import io.nekohasekai.sagernet.database.ConnStore
import io.nekohasekai.sagernet.databinding.ItemConnectionBinding
import io.nekohasekai.sagernet.databinding.LayoutConnectionsBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.ConnMonitor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Live connection list + statistics, fed by ConnMonitor (libcore tun hook).
 */
class ConnectionsFragment : ToolbarFragment(R.layout.layout_connections),
    ConnMonitor.Listener, androidx.appcompat.widget.Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutConnectionsBinding
    private lateinit var adapter: ConnectionAdapter
    private lateinit var statsAdapter: StatsAdapter
    private var tab = 0
    private var query = ""
    private var outboundFilter = FILTER_ALL
    private var statsRows: List<AggregateRow> = emptyList()

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_PROXY = 1
        private const val FILTER_DIRECT = 2
        private const val FILTER_BLOCK = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = LayoutConnectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.conn_title)
        toolbar.inflateMenu(R.menu.connections_menu)
        toolbar.setOnMenuItemClickListener(this)

        adapter = ConnectionAdapter()
        statsAdapter = StatsAdapter()
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.conn_tab_list))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.conn_tab_stats))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                this@ConnectionsFragment.tab = tab.position
                applyTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString()?.trim().orEmpty()
                refresh()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val filters = listOf(
            FILTER_ALL to R.string.conn_filter_all,
            FILTER_PROXY to R.string.conn_filter_proxy,
            FILTER_DIRECT to R.string.conn_filter_direct,
            FILTER_BLOCK to R.string.conn_filter_block,
        )
        for ((id, label) in filters) {
            val chip = Chip(requireContext()).apply {
                text = getString(label)
                isCheckable = true
                isChecked = id == FILTER_ALL
                tag = id
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        outboundFilter = id
                        refresh()
                    }
                }
            }
            binding.filters.addView(chip)
        }

        binding.refresh.setOnRefreshListener {
            binding.refresh.isRefreshing = false
            refresh()
        }

        ConnMonitor.register(this)
        refresh()
    }

    override fun onDestroyView() {
        ConnMonitor.unregister(this)
        super.onDestroyView()
    }

    override fun onConnectionsChanged() {
        view?.post { refresh() }
    }

    private fun applyTab() {
        binding.list.adapter = if (tab == 0) adapter else statsAdapter
        refresh()
    }

    private fun matches(row: ConnEntity): Boolean {
        if (query.isNotEmpty()) {
            val hit = row.domain.contains(query, true) ||
                    row.ip.contains(query) ||
                    row.packageName.contains(query, true) ||
                    row.label.contains(query, true)
            if (!hit) return false
        }
        return when (outboundFilter) {
            FILTER_PROXY -> row.outbound.contains("proxy") || row.outbound == "agent"
            FILTER_DIRECT -> row.outbound == "direct" || row.outbound == "bypass"
            FILTER_BLOCK -> row.outbound == "block"
            else -> true
        }
    }

    private fun refresh() {
        if (!isAdded) return
        if (tab == 0) {
            val rows = ConnMonitor.snapshot().filter(::matches)
            adapter.submit(rows)
        } else {
            runOnDefaultDispatcher {
                val domains = ConnStore.topDomains(24, 200)
                val apps = ConnStore.topApps(24, 100)
                val merged = if (query.isEmpty() && outboundFilter == FILTER_ALL) {
                    domains
                } else {
                    domains.filter { it.name.contains(query, true) }
                }
                val appsMerged = if (query.isEmpty()) apps else apps.filter { it.name.contains(query, true) }
                onMainDispatcher {
                    if (!isAdded) return@onMainDispatcher
                    statsRows = (merged + appsMerged)
                    statsAdapter.submit(merged, appsMerged)
                }
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_conn_pause -> {
                ConnMonitor.paused = !ConnMonitor.paused
                item.title = getString(
                    if (ConnMonitor.paused) R.string.conn_resume else R.string.conn_pause
                )
                return true
            }
            R.id.action_conn_clear -> {
                ConnMonitor.clear()
                refresh()
                return true
            }
            R.id.action_conn_export -> {
                runOnDefaultDispatcher {
                    val file = try {
                        ConnStore.exportCsv()
                    } catch (e: Exception) {
                        onMainDispatcher { snackbar(e.readableMessage).show() }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        val uri = FileProvider.getUriForFile(
                            requireContext(), BuildConfig.APPLICATION_ID + ".cache", file
                        )
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/csv")
                                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(Intent.EXTRA_STREAM, uri),
                                getString(R.string.conn_export)
                            )
                        )
                    }
                }
                return true
            }
        }
        return false
    }


    // ---- adapters ----

    private inner class ConnectionAdapter :
        RecyclerView.Adapter<ConnectionAdapter.Holder>() {

        private val rows = ArrayList<ConnEntity>()
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun submit(newRows: List<ConnEntity>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        inner class Holder(val binding: ItemConnectionBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            val b = holder.binding
            val host = row.domain.ifBlank { row.ip }
            b.domain.text = if (row.port > 0) "$host:${row.port}" else host
            b.outbound.text = outboundLabel(row.outbound)
            b.outbound.setTextColor(outboundColor(row.outbound))
            val app = row.label.ifBlank { row.packageName }
            b.app.text = app.ifBlank { "uid ${row.uid}" }
            b.traffic.text = "↑${formatBytes(row.upload)} ↓${formatBytes(row.download)}"
            b.meta.text = buildString {
                append(timeFormat.format(Date(row.time)))
                append(" · ")
                append(row.network)
                if (row.duration > 0) {
                    append(" · ")
                    append(row.duration).append("ms")
                }
            }
            b.root.setOnClickListener {
                val detail = buildString {
                    append("time: ").append(timeFormat.format(Date(row.time))).append('\n')
                    if (row.domain.isNotBlank()) append("domain: ").append(row.domain).append('\n')
                    append("dest: ").append(row.ip).append(':').append(row.port).append('\n')
                    append("app: ").append(app).append(" (uid ").append(row.uid).append(")\n")
                    append("outbound: ").append(outboundLabel(row.outbound)).append('\n')
                    append("network: ").append(row.network).append('\n')
                    append("traffic: ↑").append(formatBytes(row.upload))
                    append(" ↓").append(formatBytes(row.download)).append('\n')
                    append("duration: ").append(row.duration).append("ms")
                }
                alert(detail).show()
            }
        }
    }

    private inner class StatsAdapter :
        RecyclerView.Adapter<StatsAdapter.Holder>() {

        private val items = ArrayList<Pair<String, AggregateRow>>()

        fun submit(domains: List<AggregateRow>, apps: List<AggregateRow>) {
            items.clear()
            domains.forEach { items.add(getString(R.string.conn_group_domain) to it) }
            apps.forEach { items.add(getString(R.string.conn_group_app) to it) }
            notifyDataSetChanged()
        }

        inner class Holder(val binding: ItemConnectionBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val (group, row) = items[position]
            val b = holder.binding
            b.domain.text = row.name.ifBlank { "uid ${row.uid}" }
            b.outbound.text = "${row.count}×"
            b.app.text = group
            b.traffic.text = "↑${formatBytes(row.upload)} ↓${formatBytes(row.download)}"
            b.meta.text = ""
            b.root.setOnClickListener {
                query = row.name
                binding.search.setText(row.name)
                binding.tabs.getTabAt(0)?.select()
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private fun outboundLabel(tag: String): String = when {
        tag == "block" -> getString(R.string.conn_filter_block)
        tag == "direct" || tag == "bypass" -> getString(R.string.conn_filter_direct)
        tag.isEmpty() -> "-"
        else -> getString(R.string.conn_filter_proxy)
    }

    private fun outboundColor(tag: String): Int = when {
        tag == "block" -> 0xFFD32F2F.toInt()
        tag == "direct" || tag == "bypass" -> 0xFF388E3C.toInt()
        tag.isEmpty() -> 0xFF757575.toInt()
        else -> 0xFF1976D2.toInt()
    }
}
