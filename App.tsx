import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Alert,
  AppState,
  FlatList,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Pressable,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from "react-native-safe-area-context";
import {StatusBar} from "expo-status-bar";
import {LogEntry, NotiWebhook, Status} from './modules/noti-webhook';

function Button({
  label,
  onPress,
  kind = 'primary',
}: {
  label: string;
  onPress: () => void;
  kind?: 'primary' | 'ghost';
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({pressed}) => [
        styles.btn,
        kind === 'ghost' && styles.btnGhost,
        pressed && {opacity: 0.7},
      ]}>
      <Text style={[styles.btnText, kind === 'ghost' && styles.btnGhostText]}>
        {label}
      </Text>
    </Pressable>
  );
}

function fmtTime(ms: number) {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())} ${p(
    d.getDate(),
  )}/${p(d.getMonth() + 1)}`;
}

export default function App() {
  const [status, setStatus] = useState<Status | null>(null);
  const [url, setUrl] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const listRef = useRef<FlatList<LogEntry>>(null);
  const [scrolled, setScrolled] = useState(false);
  const [showTop, setShowTop] = useState(false);

  const onScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const y = e.nativeEvent.contentOffset.y;
    setScrolled(y > 4);
    setShowTop(y > 600);
  };

  const refresh = useCallback(async (syncForm = false) => {
    const s = await NotiWebhook.getStatus();
    setStatus(s);
    if (syncForm) {
      setUrl(s.url);
      setEnabled(s.enabled);
    }
    setLogs(await NotiWebhook.getLogs());
  }, []);

  useEffect(() => {
    refresh(true);
    // Re-check permission when user comes back from system settings.
    const sub = AppState.addEventListener('change', st => {
      if (st === 'active') refresh();
    });
    const timer = setInterval(() => refresh(), 3000);
    return () => {
      sub.remove();
      clearInterval(timer);
    };
  }, [refresh]);

  const save = async (nextEnabled = enabled) => {
    const u = url.trim();
    if (u && !/^https?:\/\//i.test(u)) {
      Alert.alert('URL không hợp lệ', 'URL phải bắt đầu bằng http:// hoặc https://');
      return false;
    }
    await NotiWebhook.saveConfig(u, nextEnabled);
    await refresh();
    return true;
  };

  const test = async () => {
    if (!(await save())) return;
    setBusy(true);
    try {
      const r = await NotiWebhook.sendTest();
      Alert.alert(r.ok ? 'Gửi thành công' : 'Gửi thất bại', r.result);
    } finally {
      setBusy(false);
      refresh();
    }
  };

  return (
    <SafeAreaProvider>
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      {/* Fixed header: stays put while the list scrolls under it. */}
      <View style={[styles.header, scrolled && styles.headerScrolled]}>
        <Text style={styles.h1}>Noti → Webhook</Text>
        {status && (
          <Text style={styles.version}>
            v{status.appVersion} · build {status.appVersionCode}
          </Text>
        )}
      </View>
      <FlatList
        ref={listRef}
        data={logs}
        keyExtractor={(item, i) => `${item.time}-${i}`}
        contentContainerStyle={styles.content}
        onScroll={onScroll}
        scrollEventThrottle={100}
        ListHeaderComponent={
          <View>
            {status && !status.permissionGranted && (
              <View style={styles.warn}>
                <Text style={styles.warnText}>
                  Chưa cấp quyền đọc thông báo. Bấm nút dưới, tìm "NotiWebhook" và
                  bật lên.
                </Text>
                <Button
                  label="Cấp quyền đọc thông báo"
                  onPress={NotiWebhook.openPermissionSettings}
                />
              </View>
            )}

            {status && status.permissionGranted && !status.batteryUnrestricted && (
              <View style={styles.warn}>
                <Text style={styles.warnText}>
                  Nên tắt tối ưu pin để Android không kill app khi chạy nền.
                </Text>
                <Button
                  label="Tắt tối ưu pin"
                  onPress={NotiWebhook.requestIgnoreBatteryOptimization}
                />
              </View>
            )}

            {status && status.permissionGranted && !status.listenerConnected && (
              <View style={styles.warn}>
                <Text style={styles.warnText}>
                  Dịch vụ đọc thông báo đang chưa chạy (thường do app bị vuốt tắt).
                  App đang tự kết nối lại; nếu cảnh báo này không mất sau vài giây,
                  tắt rồi bật lại quyền đọc thông báo.
                </Text>
                <Button
                  label="Mở quyền đọc thông báo"
                  onPress={NotiWebhook.openPermissionSettings}
                />
              </View>
            )}

            {status &&
              status.permissionGranted &&
              status.isXiaomi &&
              !status.autostartAck && (
                <View style={styles.warn}>
                  <Text style={styles.warnText}>
                    Máy Xiaomi: vuốt tắt app trong đa nhiệm sẽ dừng hẳn việc
                    chuyển tiếp. Bật "Tự khởi chạy" cho Noti Webhook, và khoá app
                    trong đa nhiệm (kéo app xuống, chọn biểu tượng ổ khoá). Hệ
                    thống không cho app biết anh đã bật chưa, nên bật xong hãy bấm
                    "Đã bật rồi".
                  </Text>
                  <View style={styles.actions}>
                    <Button
                      label="Mở Tự khởi chạy"
                      onPress={NotiWebhook.openAutostartSettings}
                    />
                    <Button
                      label="Đã bật rồi"
                      kind="ghost"
                      onPress={() => {
                        NotiWebhook.setAutostartAck(true);
                        setStatus({...status, autostartAck: true});
                      }}
                    />
                  </View>
                </View>
              )}

            <Text style={styles.label}>Webhook URL</Text>
            <TextInput
              value={url}
              onChangeText={setUrl}
              onEndEditing={() => save()}
              placeholder="https://example.com/webhook"
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
              style={styles.input}
            />

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Đang chuyển tiếp thông báo</Text>
              <Switch
                value={enabled}
                onValueChange={v => {
                  setEnabled(v);
                  save(v);
                }}
              />
            </View>

            <View style={styles.actions}>
              <Button label="Lưu" onPress={() => save()} />
              <Button
                label={busy ? 'Đang gửi…' : 'Gửi thử'}
                kind="ghost"
                onPress={busy ? () => {} : test}
              />
            </View>

            <View style={styles.logHeader}>
              <Text style={styles.h2}>Lịch sử gửi</Text>
              {logs.length > 0 && (
                <Pressable
                  onPress={() => {
                    NotiWebhook.clearLogs();
                    setLogs([]);
                  }}>
                  <Text style={styles.link}>Xoá</Text>
                </Pressable>
              )}
            </View>
          </View>
        }
        ListEmptyComponent={
          <Text style={styles.empty}>Chưa có thông báo nào được gửi.</Text>
        }
        renderItem={({item}) => (
          <View style={styles.log}>
            <View style={[styles.dot, {backgroundColor: item.ok ? '#2f9e44' : '#e03131'}]} />
            <View style={{flex: 1}}>
              <Text style={styles.logApp} numberOfLines={1}>
                {item.app} · <Text style={styles.logMeta}>{fmtTime(item.time)}</Text>
              </Text>
              {!!item.title && (
                <Text style={styles.logTitle} numberOfLines={1}>
                  {item.title}
                </Text>
              )}
              {!!item.text && (
                <Text style={styles.logText} numberOfLines={2}>
                  {item.text}
                </Text>
              )}
              <Text style={styles.logMeta}>{item.result}</Text>
            </View>
          </View>
        )}
      />
      {showTop && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Lên đầu trang"
          onPress={() => listRef.current?.scrollToOffset({offset: 0, animated: true})}
          style={({pressed}) => [styles.toTop, pressed && {opacity: 0.7}]}>
          <Text style={styles.toTopText}>↑</Text>
        </Pressable>
      )}
    </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#f4f4f2'},
  content: {paddingHorizontal: 16, paddingTop: 4, paddingBottom: 96},
  h1: {fontSize: 26, fontWeight: '700', color: '#1c1c1a'},
  h2: {fontSize: 17, fontWeight: '700', color: '#1c1c1a'},
  label: {fontSize: 13, color: '#5c5c57', marginBottom: 6},
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#d9d9d4',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: '#1c1c1a',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 14,
  },
  rowLabel: {fontSize: 15, color: '#1c1c1a'},
  actions: {flexDirection: 'row', gap: 10, marginTop: 16},
  btn: {
    backgroundColor: '#1c1c1a',
    paddingVertical: 11,
    paddingHorizontal: 18,
    borderRadius: 8,
    alignItems: 'center',
  },
  btnGhost: {backgroundColor: 'transparent', borderWidth: 1, borderColor: '#1c1c1a'},
  btnText: {color: '#fff', fontWeight: '600', fontSize: 15},
  btnGhostText: {color: '#1c1c1a'},
  warn: {
    backgroundColor: '#fff4e0',
    borderLeftWidth: 3,
    borderLeftColor: '#e8890c',
    padding: 12,
    borderRadius: 6,
    marginBottom: 16,
    gap: 10,
  },
  warnText: {color: '#5a3a00', fontSize: 14},
  logHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 28,
    marginBottom: 8,
  },
  link: {color: '#c92a2a', fontSize: 14},
  empty: {color: '#8a8a84', fontSize: 14, paddingVertical: 12},
  log: {
    flexDirection: 'row',
    gap: 10,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#d9d9d4',
  },
  dot: {width: 8, height: 8, borderRadius: 4, marginTop: 6},
  logApp: {fontSize: 14, fontWeight: '600', color: '#1c1c1a'},
  logTitle: {fontSize: 14, color: '#1c1c1a', marginTop: 2},
  logText: {fontSize: 13, color: '#5c5c57', marginTop: 1},
  logMeta: {fontSize: 12, color: '#8a8a84', fontWeight: '400', marginTop: 2},
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 12,
    backgroundColor: '#f4f4f2',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'transparent',
  },
  headerScrolled: {borderBottomColor: '#d9d9d4'},
  version: {fontSize: 12, color: '#8a8a84'},
  toTop: {
    position: 'absolute',
    right: 16,
    bottom: 24,
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#1c1c1a',
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
  },
  toTopText: {color: '#fff', fontSize: 22, fontWeight: '700', marginTop: -2},
});
