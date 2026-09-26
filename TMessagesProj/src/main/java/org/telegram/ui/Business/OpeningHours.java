package org.telegram.ui.Business;

import static org.telegram.messenger.LocaleController.formatString;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

// LoogriGram: the reading half of OpeningHoursActivity, which edited our own
// Business opening hours and is gone. Other people's hours, on their
// profiles (ProfileHoursCell) and in the profile's copy action, are still
// drawn with these.
public final class OpeningHours {

    private OpeningHours() {
    }

    public static ArrayList<TL_account.TL_businessWeeklyOpen> adaptWeeklyOpen(ArrayList<TL_account.TL_businessWeeklyOpen> hours, int utc_offset) {
        ArrayList<TL_account.TL_businessWeeklyOpen> array = new ArrayList<>(hours);

        ArrayList<TL_account.TL_businessWeeklyOpen> array2 = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            TL_account.TL_businessWeeklyOpen weekly = array.get(i);
            TL_account.TL_businessWeeklyOpen newWeekly = new TL_account.TL_businessWeeklyOpen();

            if (utc_offset != 0) {
                int start = weekly.start_minute % (24 * 60);
                int end = start + (weekly.end_minute - weekly.start_minute);
                if (start == 0 && (end == 24 * 60 || end == 24 * 60 - 1)) {
                    newWeekly.start_minute = weekly.start_minute;
                    newWeekly.end_minute = weekly.end_minute;
                    array2.add(newWeekly);
                    continue;
                }
            }

            newWeekly.start_minute = weekly.start_minute + utc_offset;
            newWeekly.end_minute = weekly.end_minute + utc_offset;
            array2.add(newWeekly);

            if (newWeekly.start_minute < 0) {
                if (newWeekly.end_minute < 0) {
                    newWeekly.start_minute += 24 * 7 * 60;
                    newWeekly.end_minute += 24 * 7 * 60;
                } else {
                    newWeekly.start_minute = 0;

                    newWeekly = new TL_account.TL_businessWeeklyOpen();
                    newWeekly.start_minute = 24 * 7 * 60 + weekly.start_minute + utc_offset;
                    newWeekly.end_minute = (24 * 7 * 60 - 1);
                    array2.add(newWeekly);
                }
            } else if (newWeekly.end_minute > 24 * 7 * 60) {
                if (newWeekly.start_minute > 24 * 7 * 60) {
                    newWeekly.start_minute -= 24 * 7 * 60;
                    newWeekly.end_minute -= 24 * 7 * 60;
                } else {
                    newWeekly.end_minute = 24 * 7 * 60 - 1;

                    newWeekly = new TL_account.TL_businessWeeklyOpen();
                    newWeekly.start_minute = 0;
                    newWeekly.end_minute = weekly.end_minute + utc_offset - (24 * 7 * 60 - 1);
                    array2.add(newWeekly);
                }
            }
        }

        Collections.sort(array2, (a, b) -> a.start_minute - b.start_minute);
        return array2;
    }

    public static ArrayList<Period>[] getDaysHours(ArrayList<TL_account.TL_businessWeeklyOpen> hours) {
        ArrayList<Period>[] days = new ArrayList[7];
        for (int i = 0; i < days.length; ++i) {
            days[i] = new ArrayList<>();
        }
        for (int i = 0; i < hours.size(); ++i) {
            TL_account.TL_businessWeeklyOpen period = hours.get(i);
            int day = (int) (period.start_minute / (24 * 60)) % 7;
            int start = period.start_minute % (24 * 60);
            int end = start + (period.end_minute - period.start_minute);
            days[day].add(new Period(start, end));
        }
        for (int i = 0; i < 7; ++i) {
            int start = (24 * 60) * i;
            int end = (24 * 60) * (i + 1);

            int m = start;
            for (int j = 0; j < hours.size(); ++j) {
                TL_account.TL_businessWeeklyOpen period = hours.get(j);
                if (period.start_minute <= m && period.end_minute >= m) {
                    m = period.end_minute + 1;
                }
            }

            boolean isFull = m >= end;
            if (isFull) {
                int prevDay = (7 + i - 1) % 7;
                if (!days[prevDay].isEmpty() && days[prevDay].get(days[prevDay].size() - 1).end >= 24 * 60) {
                    days[prevDay].get(days[prevDay].size() - 1).end = 24 * 60 - 1;
                }

                int periodEnd = Math.min(m - start - 1, 24 * 60 * 2 - 1);
                ArrayList<Period> nextDay = days[(7 + i + 1) % 7];
                if (periodEnd >= 24 * 60 && !nextDay.isEmpty() && nextDay.get(0).start < periodEnd - 24 * 60) {
                    periodEnd = 24 * 60 + nextDay.get(0).start - 1;
                }

                days[i].clear();
                days[i].add(new Period(0, periodEnd));
            } else {
                int nextDay = (i + 1) % 7;
                if (!days[i].isEmpty() && !days[nextDay].isEmpty()) {
                    Period todayLast = days[i].get(days[i].size() - 1);
                    Period tomorrowFirst = days[nextDay].get(0);
                    if (todayLast.end > 24 * 60 && todayLast.end - 24 * 60 + 1 == tomorrowFirst.start) {
                        todayLast.end = 24 * 60 - 1;
                        tomorrowFirst.start = 0;
                    }
                }
            }
        }
        return days;
    }

    public static String toString(int currentAccount, TLRPC.User user, TL_account.TL_businessWorkHours business_work_hours) {
        if (business_work_hours == null) return null;
        ArrayList<Period>[] days = getDaysHours(business_work_hours.weekly_open);
        StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append(formatString(R.string.BusinessHoursCopyHeader, UserObject.getUserName(user))).append("\n");
        }
        for (int i = 0; i < days.length; ++i) {
            ArrayList<Period> periods = days[i];
            String day = DayOfWeek.values()[i].getDisplayName(TextStyle.FULL, LocaleController.getInstance().getCurrentLocale());
            day = day.substring(0, 1).toUpperCase() + day.substring(1);
            sb.append(day).append(": ");
            if (isFull(periods)) {
                sb.append(LocaleController.getString(R.string.BusinessHoursProfileOpen));
            } else if (periods.isEmpty()) {
                sb.append(LocaleController.getString(R.string.BusinessHoursProfileClose));
            } else {
                for (int j = 0; j < periods.size(); ++j) {
                    if (j > 0) sb.append(", ");
                    Period p = periods.get(j);
                    sb.append(Period.timeToString(p.start));
                    sb.append(" - ");
                    sb.append(Period.timeToString(p.end));
                }
            }
            sb.append("\n");
        }
        TLRPC.TL_timezone timezone = TimezonesController.getInstance(currentAccount).findTimezone(business_work_hours.timezone_id);
        Calendar calendar = Calendar.getInstance();
        int currentUtcOffset = calendar.getTimeZone().getRawOffset() / 1000;
        int valueUtcOffset = timezone == null ? 0 : timezone.utc_offset;
        int utcOffset = (currentUtcOffset - valueUtcOffset) / 60;
        if (utcOffset != 0 && timezone != null) {
            sb.append(formatString(R.string.BusinessHoursCopyFooter, TimezonesController.getInstance(currentAccount).getTimezoneName(timezone, true)));
        }
        return sb.toString();
    }

    public static class Period {
        // from 0 to 2 * 24 * 60
        public int start;
        public int end;

        public Period(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @NonNull
        @Override
        public String toString() {
            return timeToString(start) + " - " + timeToString(end);
        }

        public static String timeToString(int time) {
            return timeToString(time, true);
        }

        public static String timeToString(int time, boolean includeNextDay) {
            int min = time % 60;
            int hours = (time - min) / 60 % 24;
            Calendar rightNow = Calendar.getInstance();
            rightNow.set(0, 0, 0, hours, min);
            String str = LocaleController.getInstance().getFormatterConstDay().format(rightNow.getTime());
            if (time > 24 * 60 && includeNextDay) {
                return LocaleController.formatString(R.string.BusinessHoursNextDay, str);
            }
            return str;
        }
    }

    public static boolean is24x7(TL_account.TL_businessWorkHours hours) {
        if (hours == null || hours.weekly_open.isEmpty()) return false;
        int last = 0;
        for (int i = 0; i < hours.weekly_open.size(); ++i) {
            TL_account.TL_businessWeeklyOpen period = hours.weekly_open.get(i);
            if (period.start_minute > last + 1) return false;
            last = period.end_minute;
        }
        return last >= 24 * 60 * 7 - 1;
    }

    public static boolean isFull(ArrayList<Period> periods) {
        if (periods == null || periods.isEmpty()) return false;
        int lastTime = 0;
        for (int i = 0; i < periods.size(); ++i) {
            Period p = periods.get(i);
            if (lastTime < p.start) {
                return false;
            }
            lastTime = p.end;
        }
        return lastTime == 24 * 60 - 1 || lastTime == 24 * 60;
    }
}
