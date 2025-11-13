import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { DropdownOption, ROW_HEIGHT } from "@/types/shared";
import { Check, Rows3 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

type DataTableRowHeightSelectorProps = {
  type: string;
  setType: (type: ROW_HEIGHT) => void;
};

const DataTableRowHeightSelector: React.FunctionComponent<
  DataTableRowHeightSelectorProps
> = ({ type, setType }) => {
  const { t } = useTranslation();

  const OPTIONS: DropdownOption<ROW_HEIGHT>[] = useMemo(
    () => [
      { value: ROW_HEIGHT.small, label: t("common.rowHeight.small") },
      { value: ROW_HEIGHT.medium, label: t("common.rowHeight.medium") },
      { value: ROW_HEIGHT.large, label: t("common.rowHeight.large") },
    ],
    [t],
  );

  const handleSelect = useCallback(
    (value: ROW_HEIGHT) => {
      setType(value);
    },
    [setType],
  );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          <Rows3 className="mr-1.5 size-3.5" />
          {t("common.rows")}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {OPTIONS.map(({ value, label }) => (
          <DropdownMenuItem key={value} onClick={() => handleSelect(value)}>
            <div className="relative flex w-full items-center pl-4">
              {type === value && <Check className="absolute -left-2 size-4" />}
              <span>{label}</span>
            </div>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default DataTableRowHeightSelector;
