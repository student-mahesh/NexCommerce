import React from 'react';
import { Sparkles, Database, Zap, ShieldCheck } from 'lucide-react';

export default function HeroBanner() {
  return (
    <div className="relative rounded-3xl my-6 shadow-xl overflow-hidden text-white border border-slate-800">
      {/* Background genuine electronics photograph */}
      <img
        src="/images/banners/electronics-hero.jpg"
        alt="Electronics technology background"
        className="absolute inset-0 w-full h-full object-cover object-center filter brightness-[0.25]"
      />

      {/* Gradient overlay for readability and brand tone */}
      <div className="absolute inset-0 bg-gradient-to-r from-slate-950/95 via-indigo-950/85 to-slate-950/90 pointer-events-none" />

      <div className="max-w-4xl mx-auto text-center relative z-10 py-12 px-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/20 border border-indigo-400/30 text-indigo-300 text-xs font-semibold uppercase tracking-wider mb-4">
          <Sparkles className="w-3.5 h-3.5" />
          Production-Grade Microservices
        </div>
        <h1 className="text-3xl md:text-5xl font-extrabold tracking-tight mb-4 leading-tight">
          High-Performance Gear, <br className="hidden sm:inline" />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-cyan-300 to-sky-400">
            Powered by Event-Driven Architecture
          </span>
        </h1>
        <p className="text-slate-300 text-base md:text-lg max-w-2xl mx-auto mb-8 font-normal leading-relaxed">
          Experience sub-20ms catalog reads accelerated with Redis Cache-Aside, backed by autonomous Spring Boot microservices and Apache Kafka Saga Choreography.
        </p>

        {/* Feature Pills */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-left max-w-3xl mx-auto">
          <div className="bg-slate-900/60 border border-white/10 rounded-2xl p-3 backdrop-blur-md">
            <Zap className="w-4 h-4 text-amber-400 mb-1" />
            <div className="text-xs font-bold text-white">Redis Cache-Aside</div>
            <div className="text-[11px] text-slate-400">Sub-20ms reads</div>
          </div>
          <div className="bg-slate-900/60 border border-white/10 rounded-2xl p-3 backdrop-blur-md">
            <Database className="w-4 h-4 text-emerald-400 mb-1" />
            <div className="text-xs font-bold text-white">MySQL Isolated</div>
            <div className="text-[11px] text-slate-400">Database per service</div>
          </div>
          <div className="bg-slate-900/60 border border-white/10 rounded-2xl p-3 backdrop-blur-md">
            <Sparkles className="w-4 h-4 text-indigo-400 mb-1" />
            <div className="text-xs font-bold text-white">Apache Kafka</div>
            <div className="text-[11px] text-slate-400">Event choreography</div>
          </div>
          <div className="bg-slate-900/60 border border-white/10 rounded-2xl p-3 backdrop-blur-md">
            <ShieldCheck className="w-4 h-4 text-sky-400 mb-1" />
            <div className="text-xs font-bold text-white">Auto Compensation</div>
            <div className="text-[11px] text-slate-400">Zero ghost stock</div>
          </div>
        </div>
      </div>
    </div>
  );
}
