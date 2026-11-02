import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Clock, ShieldCheck, FileCode, CheckCircle2 } from 'lucide-react';

export default function AdminPlaceholderPage({ title, milestone, plannedFeatures }) {
  return (
    <div className="space-y-6">
      {/* Breadcrumb / Back Link */}
      <div>
        <Link
          to="/admin"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-400 hover:text-indigo-300 text-decoration-none transition"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Overview</span>
        </Link>
      </div>

      {/* Main Card */}
      <div className="rounded-2xl bg-[#172338] border border-[#29384F] p-8 sm:p-12 text-center max-w-3xl mx-auto shadow-sm">
        <div className="w-16 h-16 rounded-2xl bg-indigo-950/80 border border-indigo-700/60 flex items-center justify-center text-indigo-400 mx-auto mb-6 shadow-inner">
          <Clock className="w-8 h-8" />
        </div>

        <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-950/60 border border-amber-800 text-xs font-bold text-amber-300 mb-4">
          <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
          <span>Planned Feature &bull; {milestone}</span>
        </div>

        <h2 className="text-xl sm:text-2xl font-black text-white tracking-tight mb-3">
          {title}
        </h2>

        <p className="text-sm text-slate-300 max-w-lg mx-auto mb-8 leading-relaxed">
          The routing foundation, admin layout, and responsive navigation shell for this module are established. Functional CRUD and state management operations will be integrated in the upcoming milestone step.
        </p>

        {/* Feature Checklist Box */}
        {plannedFeatures && plannedFeatures.length > 0 && (
          <div className="rounded-xl bg-[#0F172A] border border-[#29384F] p-5 text-left max-w-md mx-auto mb-8">
            <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
              <FileCode className="w-4 h-4 text-indigo-400" />
              <span>Scope of Implementation</span>
            </div>
            <ul className="space-y-2.5 mb-0 pl-0 list-none">
              {plannedFeatures.map((feat, idx) => (
                <li key={idx} className="flex items-start gap-2.5 text-xs text-slate-300">
                  <CheckCircle2 className="w-4 h-4 text-indigo-400 flex-shrink-0 mt-0.5" />
                  <span>{feat}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Action Button */}
        <div>
          <Link
            to="/admin"
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition shadow-md shadow-indigo-600/20 text-decoration-none"
          >
            <span>Return to Dashboard Overview</span>
          </Link>
        </div>
      </div>
    </div>
  );
}

