import React from 'react';
import { Zap, Heart, CheckCircle2 } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="bg-slate-900 text-slate-400 py-12 mt-20 border-t border-slate-800">
      <div className="container">
        <div className="row g-4 justify-content-between">
          <div className="col-12 col-md-5">
            <div className="d-flex align-items-center gap-2 mb-3">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white">
                <Zap className="w-4 h-4 fill-current" />
              </div>
              <span className="text-lg font-bold text-white tracking-tight">NexCommerce</span>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed mb-3">
              An enterprise-grade, interview-ready Event-Driven Microservices platform. Built with Java 21, Spring Boot 3, Apache Kafka Saga Choreography, Redis Cache-Aside, and React.
            </p>
            <div className="flex flex-wrap gap-2">
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                Java 21 LTS
              </span>
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                Spring Boot 3.3.4
              </span>
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                Apache Kafka
              </span>
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                Redis Cache
              </span>
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                MySQL 8.4
              </span>
              <span className="badge bg-slate-800 text-slate-300 border border-slate-700 text-[10px]">
                React + Vite
              </span>
            </div>
          </div>

          <div className="col-6 col-md-3">
            <h5 className="text-xs font-bold uppercase tracking-wider text-white mb-3">
              Architecture Pillars
            </h5>
            <ul className="list-unstyled text-xs space-y-2">
              <li className="flex items-center gap-1.5 text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> Database-per-Service
              </li>
              <li className="flex items-center gap-1.5 text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> Event-Driven Saga
              </li>
              <li className="flex items-center gap-1.5 text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> Automated Compensation
              </li>
              <li className="flex items-center gap-1.5 text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" /> Sub-20ms Redis Cache
              </li>
            </ul>
          </div>

          <div className="col-6 col-md-3">
            <h5 className="text-xs font-bold uppercase tracking-wider text-white mb-3">
              Interview Readiness
            </h5>
            <p className="text-xs text-slate-400 leading-relaxed">
              Designed specifically for technical full-stack interviews with clean, readable code and clear architectural defensibility.
            </p>
          </div>
        </div>

        <div className="border-t border-slate-800 mt-8 pt-6 d-flex flex-wrap align-items-center justify-content-between text-xs text-slate-500">
          <div>© 2026 NexCommerce Microservices. All rights reserved.</div>
          <div className="d-flex align-items-center gap-1">
            Engineered with <Heart className="w-3.5 h-3.5 text-rose-500 fill-current" /> for Java Full Stack Developers
          </div>
        </div>
      </div>
    </footer>
  );
}
