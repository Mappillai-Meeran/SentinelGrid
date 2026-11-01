import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Building2,
  AlertTriangle,
  ExternalLink,
  CheckCircle2,
  FileCheck2,
  QrCode,
  ArrowLeft,
  Clock,
  Sparkles,
  Download,
} from 'lucide-react';
import zohoQrImage from '../assets/zoho-pharmacy-qr.png';
import brandMarkImg from '../assets/logo/brand-mark.png';

interface PharmacyPartnershipPageProps {
  isPublic?: boolean;
}

const ZOHO_FORM_URL =
  'https://forms.zohopublic.in/mappillaimeeran32gm1/form/SentinelGridPharmacyPartnershipApplication/formperma/hauJELm0ZAOaWo-yMQqPxNlQdWcDCLX6imczzj2Eng4';

export const PharmacyPartnershipPage: React.FC<PharmacyPartnershipPageProps> = ({ isPublic = false }) => {
  const [iframeLoaded, setIframeLoaded] = useState(false);

  return (
    <div className={isPublic ? 'min-h-screen bg-slate-50 text-slate-800' : 'space-y-8'}>
      {/* Public Header when viewed outside the authenticated Layout */}
      {isPublic && (
        <>
          <div className="bg-amber-50/90 border-b border-amber-200/80 px-4 py-1.5 text-center text-[11px] font-medium text-amber-900 flex items-center justify-center gap-2">
            <span className="font-bold uppercase tracking-wider text-[9px] bg-amber-200/80 px-1.5 py-0.5 rounded text-amber-800">
              Portfolio Demo
            </span>
            <span className="truncate">
              Synthetic stock & demonstration pharmacy network. Not for real medicine fulfillment. Dial 108/112 in emergencies.
            </span>
          </div>

          <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur-md px-4 sm:px-8 py-3.5 flex items-center justify-between">
            <Link to="/" className="flex items-center gap-3" aria-label="SentinelGrid home">
              <img src={brandMarkImg} alt="SentinelGrid" className="h-9 w-9 shrink-0 object-contain rounded-lg" />
              <div>
                <p className="text-sm font-extrabold tracking-tight text-slate-950">SentinelGrid</p>
                <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">Emergency medicine network</p>
              </div>
            </Link>

            <div className="flex items-center gap-3">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                <span>Return to Sign In</span>
              </Link>
            </div>
          </header>
        </>
      )}

      {/* Main Container */}
      <div className={isPublic ? 'max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8' : 'space-y-8'}>
        {/* Page Hero Banner */}
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-blue-700 via-blue-800 to-indigo-900 p-6 sm:p-10 text-white shadow-lg">
          <div className="absolute -right-16 -top-16 h-64 w-64 rounded-full bg-blue-500/20 blur-3xl pointer-events-none" />
          <div className="absolute -bottom-16 -left-16 h-64 w-64 rounded-full bg-indigo-500/20 blur-3xl pointer-events-none" />

          <div className="relative z-10 max-w-3xl space-y-4">
            <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-blue-100 backdrop-blur-sm border border-white/15">
              <Building2 className="h-3.5 w-3.5 text-blue-200" />
              <span>Pharmacy Network Onboarding</span>
            </div>

            <h1 className="text-2xl sm:text-4xl font-extrabold tracking-tight text-white">
              Become a SentinelGrid Pharmacy Partner
            </h1>

            <p className="text-sm sm:text-base text-blue-100 leading-relaxed max-w-2xl">
              Apply to join the SentinelGrid pharmacy network. Submit your details for verification by our team.
              Help bridge critical emergency medicine shortages in your locality with automated reservation coordination.
            </p>

            <div className="pt-2 flex flex-wrap items-center gap-3 text-xs text-blue-200">
              <span className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-lg">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                Verified Licensed Pharmacies Only
              </span>
              <span className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-lg">
                <FileCheck2 className="w-4 h-4 text-blue-300 shrink-0" />
                Form 20/21 Compliance
              </span>
              <span className="flex items-center gap-1.5 bg-white/10 px-3 py-1.5 rounded-lg">
                <Clock className="w-4 h-4 text-amber-300 shrink-0" />
                24-48h Review Turnaround
              </span>
            </div>
          </div>
        </div>

        {/* Mandatory Verification & Approval Notice */}
        <div className="rounded-2xl border border-amber-200 bg-amber-50/90 p-4 sm:p-5 text-amber-900 shadow-xs flex items-start gap-3.5">
          <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-amber-100 text-amber-700">
            <AlertTriangle className="h-5 w-5" />
          </div>
          <div className="space-y-1 text-xs sm:text-sm">
            <p className="font-bold text-amber-950">Important Notice on Pharmacy Activation</p>
            <p className="text-amber-800 leading-relaxed">
              <strong>Submitting an application does not automatically approve or activate a pharmacy.</strong> All
              submissions undergo rigorous manual compliance screening by SentinelGrid administrators. Drug License
              numbers, GSTIN details, registered pharmacist credentials, and branch operating hours are strictly verified
              before branch portal credentials and inventory tracking are provisioned.
            </p>
          </div>
        </div>

        {/* Grid: Main Application Form + Supporting Cards */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Main Embedded Zoho Form (8 cols on desktop) */}
          <div className="lg:col-span-8 space-y-4">
            <div className="rounded-2xl border border-slate-200/90 bg-white p-4 sm:p-6 shadow-sm">
              <div className="mb-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100">
                <div>
                  <h2 className="text-lg font-bold text-slate-900">Pharmacy Partnership Application</h2>
                  <p className="text-xs text-slate-500">Official onboarding portal powered by Zoho Forms</p>
                </div>

                {/* Direct Fallback Link */}
                <a
                  href={ZOHO_FORM_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50/70 px-3 py-1.5 text-xs font-semibold text-blue-700 hover:bg-blue-100 hover:text-blue-800 transition-colors shrink-0"
                >
                  <span>Open form in new tab</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>

              {/* Exact Zoho iframe Container */}
              <div className="relative min-h-[520px] sm:min-h-[620px] rounded-xl overflow-hidden border border-slate-100 bg-slate-50/50">
                {!iframeLoaded && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-white/80 backdrop-blur-xs text-slate-500 z-10">
                    <div className="h-8 w-8 animate-spin rounded-full border-3 border-blue-600 border-t-transparent" />
                    <p className="text-xs font-medium">Loading partnership application form...</p>
                  </div>
                )}

                <iframe
                  aria-label="SentinelGrid – Pharmacy Partnership Application"
                  frameBorder="0"
                  style={{ height: '700px', width: '99%', border: 'none' }}
                  src={ZOHO_FORM_URL}
                  title="SentinelGrid – Pharmacy Partnership Application"
                  onLoad={() => setIframeLoaded(true)}
                  className="w-full min-h-[550px] sm:min-h-[700px] bg-transparent"
                />
              </div>

              {/* Accessibility / Trouble Viewing Notice with Fallback Link */}
              <div className="mt-4 pt-3 border-t border-slate-100 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs text-slate-500">
                <p>Having difficulty viewing the form embedded above?</p>
                <a
                  href={ZOHO_FORM_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 font-semibold text-blue-600 hover:text-blue-700 hover:underline"
                >
                  <span>Launch standalone application form</span>
                  <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>
          </div>

          {/* Right Column: QR Code + Onboarding Guidance (4 cols on desktop) */}
          <div className="lg:col-span-4 space-y-6">
            {/* Mobile Application QR Code Card */}
            <div className="rounded-2xl border border-slate-200/90 bg-white p-6 shadow-sm text-center space-y-4">
              <div className="inline-flex items-center justify-center w-10 h-10 rounded-xl bg-blue-50 text-blue-600 mb-1">
                <QrCode className="w-5 h-5" />
              </div>
              <h3 className="text-base font-bold text-slate-900">Apply via Mobile</h3>
              <p className="text-xs text-slate-500 leading-relaxed">
                Scan this QR code on any smartphone camera to open the application directly on mobile. Ideal for field
                visits, in-person pharmacy demonstrations, and quick on-site submissions.
              </p>

              <div className="p-4 bg-slate-50 border border-slate-200/80 rounded-2xl inline-block shadow-inner">
                <img
                  src={zohoQrImage}
                  alt="SentinelGrid Pharmacy Partnership Application QR Code"
                  className="w-44 h-44 object-contain mx-auto rounded-lg"
                />
              </div>

              <div className="pt-2">
                <a
                  href="/zoho-pharmacy-qr.png"
                  download="sentinelgrid-pharmacy-partnership-qr.png"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-blue-600 transition-colors"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download QR Code Image</span>
                </a>
              </div>
            </div>

            {/* What to Expect (Onboarding Timeline) */}
            <div className="rounded-2xl border border-slate-200/90 bg-white p-6 shadow-sm space-y-4">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-blue-600" />
                <span>Onboarding Process</span>
              </h3>

              <ol className="space-y-4 text-xs">
                <li className="flex items-start gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-100 font-bold text-blue-700 text-[11px]">
                    1
                  </span>
                  <div>
                    <p className="font-semibold text-slate-800">Submit Application</p>
                    <p className="text-slate-500 mt-0.5">Provide pharmacy branch details, GPS coordinates, and contact details via the form.</p>
                  </div>
                </li>

                <li className="flex items-start gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-100 font-bold text-blue-700 text-[11px]">
                    2
                  </span>
                  <div>
                    <p className="font-semibold text-slate-800">Compliance Verification</p>
                    <p className="text-slate-500 mt-0.5">Our team validates Form 20/21 drug licenses and pharmacist registration certificates.</p>
                  </div>
                </li>

                <li className="flex items-start gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-100 font-bold text-blue-700 text-[11px]">
                    3
                  </span>
                  <div>
                    <p className="font-semibold text-slate-800">Workspace Provisioning</p>
                    <p className="text-slate-500 mt-0.5">Upon approval, pharmacists receive private branch credentials to manage emergency holds.</p>
                  </div>
                </li>
              </ol>
            </div>

            {/* Help & Support Note */}
            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5 text-xs text-slate-600 space-y-2">
              <p className="font-bold text-slate-800">Have Questions?</p>
              <p className="leading-relaxed">
                For questions regarding pharmacy onboarding or status updates, contact our support desk at{' '}
                <a href="mailto:support@sentinelgrid.zohodesk.in" className="font-semibold text-blue-600 hover:underline">
                  support@sentinelgrid.zohodesk.in
                </a>
                .
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PharmacyPartnershipPage;

