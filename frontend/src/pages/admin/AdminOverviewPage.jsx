import React from 'react';
import { useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import {
  Package,
  ShoppingBag,
  Clock,
  Layers,
  ArrowRight,
  Server,
  Zap,
  CheckCircle2,
  Database,
  Radio,
  Cpu,
} from 'lucide-react';

export default function AdminOverviewPage() {
  const { user } = useSelector((state) => state.auth);

  const metricCards = [
    {
      title: 'Total Products',
      icon: Package,
      statusBadge: 'Planned M15.4',
      badgeColor: 'text-amber-400 bg-amber-950/50 border-amber-800',
      description: 'Active catalog items across all categories',
      linkTo: '/admin/products',
      linkText: 'Manage Products',
    },
    {
      title: 'Total Orders',
      icon: ShoppingBag,
      statusBadge: 'Planned M15.5',
      badgeColor: 'text-blue-400 bg-blue-950/50 border-blue-800',
      description: 'Lifetime orders placed across all customers',
      linkTo: '/admin/orders',
      linkText: 'View Orders',
    },
    {
      title: 'Pending Orders',
      icon: Clock,
      statusBadge: 'Planned M15.5',
      badgeColor: 'text-indigo-400 bg-indigo-950/50 border-indigo-800',
      description: 'Orders currently processing in Kafka Saga choreography',
      linkTo: '/admin/orders',
      linkText: 'Track Sagas',
    },
    {
      title: 'Inventory Status',
      icon: Layers,
      statusBadge: 'Planned M15.6',
      badgeColor: 'text-emerald-400 bg-emerald-950/50 border-emerald-800',
      description: 'Stock thresholds and reservation levels',
      linkTo: '/admin/inventory',
      linkText: 'Inspect Inventory',
    },
  ];

  const microservices = [
    { name: 'API Gateway', port: 8080, role: 'JWT Auth Filter, Reactive Routing & Anti-Spoofing', healthy: true },
    { name: 'Product Service', port: 8081, role: 'Catalog CRUD, Redis Cache-Aside & Admin Authorization', healthy: true },
    { name: 'Order Service', port: 8082, role: 'Order Lifecycle State Machine & Catalog Price Validation', healthy: true },
    { name: 'Inventory Service', port: 8083, role: 'Pessimistic Stock Reservation & Compensation Release', healthy: true },
    { name: 'Payment Service', port: 8084, role: 'Deterministic Payment Engine & Idempotency Protection', healthy: true },
    { name: 'Notification Service', port: 8085, role: 'Customer Timeline & Audit Log Consumers', healthy: true },
    { name: 'Auth Service', port: 8086, role: 'JWT Minting, BCrypt & Google OAuth2 Hybrid', healthy: true },
  ];

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="rounded-2xl bg-[#172338] border border-[#29384F] p-6 lg:p-8 relative overflow-hidden shadow-sm">
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-950/70 border border-indigo-800/60 text-[11px] font-bold text-indigo-300 mb-3">
              <Zap className="w-3.5 h-3.5 text-indigo-400" />
              <span>Event-Driven Microservices Architecture</span>
            </div>
            <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight mb-2">
              Welcome back, {user?.name || user?.email || 'Administrator'}
            </h2>
            <p className="text-xs sm:text-sm text-slate-300 max-w-2xl mb-0 leading-relaxed">
              This is the centralized administrative control plane for NexCommerce. Monitor platform microservices, catalog lifecycle, customer order sagas, and stock reservations.
            </p>
          </div>

          <div className="flex-shrink-0 flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-950/60 border border-emerald-800 text-xs font-semibold text-emerald-300">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              Cluster Active
            </span>
          </div>
        </div>
      </div>

      {/* Metric Cards Shell */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-0">
            Platform Metrics Overview
          </h3>
          <span className="text-[11px] text-slate-400">
            Real metrics will populate upon completion of Step 4-6 integrations
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {metricCards.map((card) => {
            const Icon = card.icon;
            return (
              <div
                key={card.title}
                className="rounded-2xl bg-[#172338] border border-[#29384F] p-5 flex flex-col justify-between hover:border-indigo-500/50 transition-colors shadow-sm"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <div className="w-10 h-10 rounded-xl bg-[#1C2A40] border border-[#29384F] flex items-center justify-center text-indigo-400">
                      <Icon className="w-5 h-5" />
                    </div>
                    <span
                      className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md border ${card.badgeColor}`}
                    >
                      {card.statusBadge}
                    </span>
                  </div>

                  <h4 className="text-sm font-bold text-white mb-1">
                    {card.title}
                  </h4>
                  <p className="text-xs text-slate-400 mb-4 leading-relaxed">
                    {card.description}
                  </p>
                </div>

                <div className="pt-3 border-t border-[#29384F] flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-amber-400/80" />
                    <span className="text-[11px] text-slate-400 font-mono">
                      Awaiting Integration
                    </span>
                  </div>
                  <Link
                    to={card.linkTo}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-400 hover:text-indigo-300 text-decoration-none transition"
                  >
                    <span>{card.linkText}</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Cluster Health & Architecture Topology */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Microservices Mesh */}
        <div className="lg:col-span-2 rounded-2xl bg-[#172338] border border-[#29384F] p-6 shadow-sm">
          <div className="flex items-center justify-between pb-4 border-b border-[#29384F] mb-4">
            <div className="flex items-center gap-2">
              <Server className="w-4 h-4 text-indigo-400" />
              <h4 className="text-sm font-bold text-white mb-0">
                Service Mesh & Port Directory
              </h4>
            </div>
            <span className="text-[11px] text-slate-400 font-mono">
              Host: localhost
            </span>
          </div>

          <div className="space-y-2.5">
            {microservices.map((svc) => (
              <div
                key={svc.name}
                className="flex items-center justify-between p-3 rounded-xl bg-[#0F172A] border border-[#29384F] hover:border-slate-600 transition"
              >
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 rounded-full bg-emerald-400" />
                  <div>
                    <span className="text-xs font-bold text-white block">
                      {svc.name}
                    </span>
                    <span className="text-[11px] text-slate-400">
                      {svc.role}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs px-2 py-0.5 rounded bg-[#172338] text-indigo-300 border border-[#29384F]">
                    :{svc.port}
                  </span>
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Infrastructure & Messaging Stack */}
        <div className="rounded-2xl bg-[#172338] border border-[#29384F] p-6 shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 pb-4 border-b border-[#29384F] mb-4">
              <Radio className="w-4 h-4 text-indigo-400" />
              <h4 className="text-sm font-bold text-white mb-0">
                Event Backbone
              </h4>
            </div>

            <div className="space-y-3">
              <div className="p-3 rounded-xl bg-[#0F172A] border border-[#29384F]">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-bold text-white">Apache Kafka (KRaft)</span>
                  <span className="text-[11px] font-mono text-indigo-300">:9094</span>
                </div>
                <p className="text-[11px] text-slate-400 mb-0">
                  Event choreography for order sagas, compensation rollbacks, and notification audit pipelines.
                </p>
              </div>

              <div className="p-3 rounded-xl bg-[#0F172A] border border-[#29384F]">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-bold text-white">Redis 8.0 Cache</span>
                  <span className="text-[11px] font-mono text-indigo-300">:6380</span>
                </div>
                <p className="text-[11px] text-slate-400 mb-0">
                  Sub-millisecond cache-aside layer for catalog browsing and category hierarchies.
                </p>
              </div>

              <div className="p-3 rounded-xl bg-[#0F172A] border border-[#29384F]">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-bold text-white">MySQL 8.4 Multi-DB</span>
                  <span className="text-[11px] font-mono text-indigo-300">:3309</span>
                </div>
                <p className="text-[11px] text-slate-400 mb-0">
                  Strict database-per-service isolation across 6 dedicated schemas.
                </p>
              </div>
            </div>
          </div>

          <div className="mt-4 pt-4 border-t border-[#29384F] text-[11px] text-slate-400 text-center">
            NexCommerce Admin Engine &bull; M15 Foundation
          </div>
        </div>
      </div>
    </div>
  );
}

