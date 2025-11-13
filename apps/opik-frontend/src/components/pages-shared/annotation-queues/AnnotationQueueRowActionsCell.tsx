import React, { useCallback, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { MoreHorizontal, Copy, Trash, Pencil } from "lucide-react";
import { CellContext } from "@tanstack/react-table";
import copy from "clipboard-copy";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/components/ui/use-toast";
import ConfirmDialog from "@/components/shared/ConfirmDialog/ConfirmDialog";
import CellWrapper from "@/components/shared/DataTableCells/CellWrapper";

import { AnnotationQueue } from "@/types/annotation-queues";
import useAnnotationQueueDeleteMutation from "@/api/annotation-queues/useAnnotationQueueBatchDeleteMutation";
import useAppStore from "@/store/AppStore";
import { generateSMEURL } from "@/lib/annotation-queues";
import AddEditAnnotationQueueDialog from "./AddEditAnnotationQueueDialog";

const AnnotationQueueRowActionsCell: React.FunctionComponent<
  CellContext<AnnotationQueue, unknown>
> = (context) => {
  const { t } = useTranslation();
  const workspaceName = useAppStore((state) => state.activeWorkspaceName);
  const resetKeyRef = useRef(0);
  const queue = context.row.original;
  const [open, setOpen] = useState<number | boolean>(false);
  const { toast } = useToast();
  const { mutate } = useAnnotationQueueDeleteMutation();

  const handleCopySMELink = useCallback(() => {
    copy(generateSMEURL(workspaceName, queue.id));
    toast({
      title: t("annotationQueues.rowActions.linkCopied"),
      description: t("annotationQueues.rowActions.linkCopiedDescription"),
    });
  }, [queue.id, toast, workspaceName, t]);

  const deleteQueueHandler = useCallback(() => {
    mutate({
      ids: [queue.id],
    });
  }, [queue.id, mutate]);

  const handleEdit = useCallback(() => {
    setOpen(2);
    resetKeyRef.current = resetKeyRef.current + 1;
  }, []);

  return (
    <CellWrapper
      metadata={context.column.columnDef.meta}
      tableMetadata={context.table.options.meta}
      className="justify-end p-0"
      stopClickPropagation
    >
      <ConfirmDialog
        key={`delete-${resetKeyRef.current}`}
        open={open === 1}
        setOpen={setOpen}
        onConfirm={deleteQueueHandler}
        title={t("annotationQueues.rowActions.deleteTitle")}
        description={t("annotationQueues.rowActions.deleteDescription")}
        confirmText={t("common.delete")}
        confirmButtonVariant="destructive"
      />
      <AddEditAnnotationQueueDialog
        key={`edit-${resetKeyRef.current}`}
        queue={queue}
        open={open === 2}
        setOpen={setOpen}
        projectId={queue.project_id}
        scope={queue.scope}
      />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="minimal" size="icon" className="-mr-2.5">
            <span className="sr-only">{t("common.actionsMenu")}</span>
            <MoreHorizontal className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          <DropdownMenuItem onClick={handleCopySMELink}>
            <Copy className="mr-2 size-4" />
            {t("annotationQueues.rowActions.copySharingLink")}
          </DropdownMenuItem>
          <DropdownMenuItem onClick={handleEdit}>
            <Pencil className="mr-2 size-4" />
            {t("common.edit")}
          </DropdownMenuItem>
          <DropdownMenuItem
            onClick={() => {
              setOpen(1);
              resetKeyRef.current = resetKeyRef.current + 1;
            }}
          >
            <Trash className="mr-2 size-4" />
            {t("common.delete")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </CellWrapper>
  );
};

export default AnnotationQueueRowActionsCell;
