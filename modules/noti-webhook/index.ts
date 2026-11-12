import {requireNativeModule} from 'expo';

export type Status = {
  url: string;
  enabled: boolean;
  permissionGranted: boolean;
  batteryUnrestricted: boolean;
  isXiaomi: boolean;
  autostartAck: boolean;
  listenerConnected: boolean;
  appVersion: string;
  appVersionCode: number;
};

export type LogEntry = {
  time: number;
  app: string;
  title: string;
  text: string;
  ok: boolean;
  result: string;
};

type NativeApi = {
  getStatus(): Promise<Status>;
  saveConfig(url: string, enabled: boolean): Promise<boolean>;
  openPermissionSettings(): void;
  requestIgnoreBatteryOptimization(): void;
  openAutostartSettings(): void;
  setAutostartAck(value: boolean): void;
  sendTest(): Promise<{ok: boolean; result: string}>;
  getLogs(): Promise<string>;
  clearLogs(): void;
};

const native = requireNativeModule<NativeApi>('NotiWebhook');

export const NotiWebhook = {
  getStatus: () => native.getStatus(),
  saveConfig: (url: string, enabled: boolean) => native.saveConfig(url, enabled),
  openPermissionSettings: () => native.openPermissionSettings(),
  requestIgnoreBatteryOptimization: () =>
    native.requestIgnoreBatteryOptimization(),
  openAutostartSettings: () => native.openAutostartSettings(),
  setAutostartAck: (value: boolean) => native.setAutostartAck(value),
  sendTest: () => native.sendTest(),
  clearLogs: () => native.clearLogs(),
  async getLogs(): Promise<LogEntry[]> {
    try {
      return JSON.parse(await native.getLogs()) as LogEntry[];
    } catch {
      return [];
    }
  },
};
