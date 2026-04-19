/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { 
  Bluetooth, 
  Settings, 
  Bell, 
  Activity, 
  Zap, 
  ShieldCheck, 
  Smartphone,
  ChevronRight,
  ExternalLink,
  Code2,
  Cpu,
  Copy,
  Check,
  History,
  Trash2,
  Scan,
  Plus,
  Power,
  X
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { cn } from './lib/utils';

// --- Types ---

interface AncsNotification {
  id: string;
  app: string;
  title: string;
  message: string;
  time: string;
  status: 'pending' | 'sent' | 'failed';
}

const ANDROID_SOURCE = {
  instructions: `BUILD INSTRUCTIONS:
1. Export this project as a ZIP (Settings -> Export).
2. Open Android Studio and choose the "android-native" folder.
3. Click "Run" to install the APK to your device.`,
  manifest: `<!-- AndroidManifest.xml -->
<manifest ...>
    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.iOSNotify">
        <service android:name=".AncsBridgeService" ... />
    </application>
</manifest>`,
  service: `// Simple ANCS Relay Logic
class AncsBridgeService : Service() {
    // ... handles GATT notifications
    // ... sends Tasker intents
}`
};

// --- Components ---

interface LogEntryProps {
  notification: AncsNotification;
}

const LogEntry: React.FC<LogEntryProps> = ({ notification }) => {
  return (
    <motion.div 
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="p-4 border-b border-white/10 hover:bg-white/5 transition-colors"
    >
      <div className="flex justify-between items-start mb-1">
        <span className="text-[10px] font-mono text-white/40 uppercase">{notification.app}</span>
        <span className="text-[10px] font-mono text-white/40">{notification.time}</span>
      </div>
      <p className="text-sm font-bold text-white">{notification.title}</p>
      <p className="text-xs text-white/60 line-clamp-2">{notification.message}</p>
    </motion.div>
  );
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'status' | 'logs'>('status');
  const [isScanning, setIsScanning] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [isRecording, setIsRecording] = useState(true);
  const [showPairModal, setShowPairModal] = useState(false);
  const [notifications, setNotifications] = useState<AncsNotification[]>([
    { id: '1', app: 'WhatsApp', title: 'John Doe', message: 'Hey, are we still meeting today?', time: '14:22:01', status: 'sent' },
    { id: '2', app: 'Instagram', title: 'Photo Like', message: 'Sarah liked your photo', time: '14:15:45', status: 'sent' },
    { id: '3', app: 'Mail', title: 'Work Update', message: 'Important: Q3 Planning details...', time: '13:02:12', status: 'sent' },
  ]);

  const toggleScan = () => {
    setIsScanning(true);
    setTimeout(() => {
      setIsScanning(false);
      setShowPairModal(true);
    }, 2000);
  };

  const handlePair = () => {
    setIsConnected(true);
    setShowPairModal(false);
  };

  const clearLogs = () => {
    setNotifications([]);
  };

  return (
    <div className="min-h-screen bg-[#000000] text-white font-sans flex flex-col items-center selection:bg-white/20">
      {/* Mobile Frame (Centering the app UI) */}
      <div className="w-full max-w-[450px] min-h-screen flex flex-col border-x border-white/10 relative shadow-2xl overflow-hidden">
        
        {/* Header */}
        <header className="p-6 pt-12 border-b border-white/10 flex justify-between items-center bg-black/50 backdrop-blur-md sticky top-0 z-40">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">iOS Notify</h1>
            <p className="text-[10px] text-white/40 font-mono tracking-widest uppercase">Android Bridge</p>
          </div>
          <button className="p-2 opacity-60 hover:opacity-100 transition-opacity">
            <Settings className="w-5 h-5" />
          </button>
        </header>

        {/* Dynamic Content */}
        <main className="flex-1 overflow-y-auto custom-scrollbar">
          <AnimatePresence mode="wait">
            {activeTab === 'status' ? (
              <motion.div 
                key="status"
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20 }}
                className="p-6 space-y-8"
              >
                {/* Device Info Card */}
                <div className="p-6 rounded-2xl bg-white/5 border border-white/10 space-y-6">
                  <div className="flex justify-between items-center">
                    <span className="text-[10px] font-mono text-white/40 uppercase tracking-widest">Paired Device</span>
                    <div className={cn(
                      "flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase",
                      isConnected ? "bg-white text-black" : "bg-white/10 text-white/60"
                    )}>
                      {isConnected ? "Connected" : "Disconnected"}
                    </div>
                  </div>
                  
                  <div className="flex items-center gap-4">
                    <div className="p-4 rounded-xl bg-white/10">
                      <Smartphone className="w-8 h-8" />
                    </div>
                    <div>
                      <h3 className="text-xl font-bold">{isConnected ? "iPhone 15 Pro" : "None"}</h3>
                      <p className="text-sm text-white/40 font-mono">{isConnected ? "79:05:F2:14:B5:CE" : "No device bonded"}</p>
                    </div>
                  </div>

                  <div className="pt-4 border-t border-white/5 flex justify-between items-end">
                    <div>
                      <p className="text-[10px] font-mono text-white/40 uppercase mb-1">Total Hits</p>
                      <p className="text-3xl font-bold tracking-tighter">{notifications.length}</p>
                    </div>
                    <div className="text-right">
                      <Zap className="w-5 h-5 text-white/20 ml-auto mb-1" />
                      <p className="text-[10px] font-mono text-white/40 uppercase">Tasker events</p>
                    </div>
                  </div>
                </div>

                {/* Scan Action */}
                {!isConnected ? (
                  <button 
                    onClick={toggleScan}
                    disabled={isScanning}
                    className={cn(
                      "w-full py-4 rounded-2xl border flex items-center justify-center gap-3 transition-all",
                      isScanning 
                        ? "border-white/10 bg-white/5 text-white/40 cursor-wait" 
                        : "border-white bg-white text-black font-bold active:scale-[0.98]"
                    )}
                  >
                    {isScanning ? (
                      <>
                        <Scan className="w-5 h-5 animate-spin" />
                        <span>Searching...</span>
                      </>
                    ) : (
                      <>
                        <Plus className="w-5 h-5" />
                        <span>Pair New Device</span>
                      </>
                    )}
                  </button>
                ) : (
                  <button 
                    onClick={() => setIsConnected(false)}
                    className="w-full py-4 rounded-2xl border border-white/10 bg-white/5 text-white font-medium hover:bg-white/10"
                  >
                    Disconnect Device
                  </button>
                )}

                {/* Integration Info */}
                <div className="p-4 bg-white/5 rounded-2xl border border-white/10 border-dashed opacity-40">
                  <div className="flex items-center gap-2 mb-2">
                    <ShieldCheck className="w-4 h-4" />
                    <span className="text-xs font-bold uppercase tracking-widest">Active Guard</span>
                  </div>
                  <p className="text-xs">BLE Encryption is active. All iOS data stays local and is relayed directly to Tasker via system intents.</p>
                </div>
              </motion.div>
            ) : (
              <motion.div 
                key="logs"
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                className="flex flex-col h-full min-h-[60vh]"
              >
                {/* Log Controls */}
                <div className="p-4 bg-black/50 backdrop-blur-sm sticky top-0 border-b border-white/10 flex justify-between items-center z-10">
                  <div className="flex items-center gap-3">
                    <button 
                      onClick={() => setIsRecording(!isRecording)}
                      className={cn(
                        "flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-bold transition-all",
                        isRecording ? "bg-white text-black" : "bg-white/10 text-white/50"
                      )}
                    >
                      <Power className="w-3 h-3" />
                      {isRecording ? "REC ON" : "REC OFF"}
                    </button>
                    {notifications.length > 0 && (
                      <button 
                        onClick={clearLogs}
                        className="p-1.5 text-white/40 hover:text-white transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                  <span className="text-[10px] font-mono text-white/40 uppercase">{notifications.length} Entries</span>
                </div>

                {/* Log List */}
                <div className="flex-1">
                  {notifications.length > 0 ? (
                    notifications.map(n => <LogEntry key={n.id} notification={n} />)
                  ) : (
                    <div className="h-full flex flex-col items-center justify-center p-12 text-center opacity-30 mt-20">
                      <History className="w-12 h-12 mb-4" />
                      <p className="text-sm">Log is empty. Incoming notifications will appear here when recording is enabled.</p>
                    </div>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </main>

        {/* Tab Bar */}
        <nav className="h-20 border-t border-white/10 bg-black/80 backdrop-blur-xl flex items-center sticky bottom-0 z-40">
          <button 
            onClick={() => setActiveTab('status')}
            className={cn(
              "flex-1 flex flex-col items-center gap-1 transition-all",
              activeTab === 'status' ? "text-white" : "text-white/40"
            )}
          >
            <Smartphone className="w-5 h-5" />
            <span className="text-[10px] font-bold uppercase tracking-widest">Status</span>
          </button>
          <button 
            onClick={() => setActiveTab('logs')}
            className={cn(
              "flex-1 flex flex-col items-center gap-1 transition-all",
              activeTab === 'logs' ? "text-white" : "text-white/40"
            )}
          >
            <History className="w-5 h-5" />
            <span className="text-[10px] font-bold uppercase tracking-widest">Logs</span>
          </button>
        </nav>

        {/* Pair Modal */}
        <AnimatePresence>
          {showPairModal && (
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 z-50 bg-black/90 backdrop-blur-xl flex items-center justify-center p-8"
            >
              <motion.div 
                initial={{ scale: 0.9, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                className="w-full bg-white/5 border border-white/10 rounded-3xl p-6 space-y-6"
              >
                <div className="flex justify-between items-center">
                  <h3 className="text-xl font-bold">Devices Found</h3>
                  <button onClick={() => setShowPairModal(false)} className="p-1 text-white/40"><X className="w-5 h-5" /></button>
                </div>
                
                <div className="space-y-2">
                  <button 
                    onClick={handlePair}
                    className="w-full p-4 rounded-xl bg-white/5 border border-white/10 flex justify-between items-center group hover:bg-white hover:text-black transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <Bluetooth className="w-5 h-5" />
                      <div className="text-left">
                        <p className="text-sm font-bold">iPhone 15 Pro</p>
                        <p className="text-[10px] font-mono opacity-60">Ready to bond</p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 opacity-40" />
                  </button>
                </div>

                <p className="text-[10px] text-center text-white/40">Ensure "Bluetooth Sharing" is enabled in iOS Settings under this app's permissions.</p>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

      </div>

      <style>{`
        .custom-scrollbar::-webkit-scrollbar {
          width: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.1);
          border-radius: 10px;
        }
      `}</style>
    </div>
  );
}
